package icu.samnyan.aqua.net

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaUserServices
import icu.samnyan.aqua.net.utils.AquaNetProps
import icu.samnyan.aqua.net.games.GenericUserDataRepo
import icu.samnyan.aqua.net.games.IGenericUserData
import icu.samnyan.aqua.net.utils.SUCCESS
import icu.samnyan.aqua.sega.chusan.model.Chu3UserDataRepo
import icu.samnyan.aqua.sega.general.dao.CardRepository
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.general.service.CardService
import icu.samnyan.aqua.sega.maimai2.model.Mai2UserDataRepo
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController
import kotlin.jvm.optionals.getOrNull

@RestController
@API("/api/v2/card")
class CardController(
    val jwt: JWT,
    val us: AquaUserServices,
    val cardService: CardService,
    val cardGameService: CardGameService,
    val cardRepository: CardRepository,
    val props: AquaNetProps
) {
    @API("/summary")
    @Doc("Get a summary of the card, including the user's name, rating, and last login date.", "Summary of the card")
    suspend fun summary(@RP cardId: Str): Any
    {
        // DO NOT CHANGE THIS ERROR MESSAGE - The frontend uses it to detect if the card is not found
        val card = cardService.tryLookup(cardId) ?: (404 - "Card not found")

        // Lookup data for each game
        return mapOf(
            "card" to card,
            "summary" to cardGameService.getSummary(card),
        )
    }

    @API("/user-games")
    @Doc("Get the game summary of the user, including the user's name, rating, and last login date.", "Summary of the user")
    suspend fun userGames(@RP username: Str) = us.cardByName(username) { card -> cardGameService.getSummary(card) }

    /**
     * Bind a card to the user. This action will migrate selected data from the card to the user's ghost card.
     *
     * Non-migrated data will not be lost, but will be inaccessible from the card until the card is unbound.
     *
     * @param token JWT token
     * @param cardId Card ID
     * @param migrate Things to migrate, stored as a comma-separated list of game IDs (e.g. "maimai2,chusan")
     */
    @API("/link")
    @Doc("Bind a card to the user. This action will migrate selected data from the card to the user's ghost card.", "Success message")
    suspend fun link(@RP token: Str, @RP cardId: Str, @RP migrate: Str) = jwt.auth(token) { u ->
        // Check if the user's card limit is reached
        if (u.cards.size >= props.linkCardLimit) 400 - "Card limit reached"

        // Try to look up the card
        val card = cardService.tryLookup(cardId)

        // If no card is found, create a new card
        if (card == null) {
            // Ensure the format of the card ID is correct
            val id = cardService.sanitizeCardId(cardId)

            // Create a new card
            cardService.registerByAccessCode(id, u)

            return SUCCESS
        }

        // If card is already bound
        if (card.aquaUser != null) 400 - "Card already bound to another user"

        // Bind the card
        card.aquaUser = u
        async { cardRepository.save(card) }

        // Migrate selected data to the new user
        val games = migrate.split(',')
        cardGameService.migrate(card, games)

        SUCCESS
    }

    @API("/unlink")
    @Doc("Unbind a card from the user. No data will be migrated during this action.", "Success message")
    suspend fun unlink(@RP token: Str, @RP cardId: Str) = jwt.auth(token) { u ->
        // Try to look up the card
        val card = cardService.tryLookup(cardId) ?: (404 - "Card not found")

        // If the card is not bound to the user
        if (card.aquaUser != u) 400 - "Card not bound to user"

        // Unbind the card
        card.aquaUser = null
        async { cardRepository.save(card) }

        SUCCESS
    }

    @API("/default-game")
    @Doc("Get the default game for the card.", "Game ID")
    suspend fun defaultGame(@RP username: Str) = us.cardByName(username) { card ->
        mapOf("game" to cardGameService.getSummary(card).filterValues { it != null }.keys.firstOrNull())
    }
}

/**
 * Migrate data from the card to the user's ghost card
 *
 * Assumption: The card is already linked to the user.
 */
suspend fun <T : IGenericUserData> migrateCard(repo: GenericUserDataRepo<T>, card: Card): Bool
{
    // Check if data already exists in the user's ghost card
    async { repo.findByCard(card.aquaUser!!.ghostCard) }?.let {
        // Unbind the data from the card
        it.card = null
        async { repo.save(it) }
    }

    // Migrate data from the card to the user's ghost card
    // An easy migration is to change the UserData card field to the user's ghost card
    val data = async { repo.findByCard(card) } ?: return false
    data.card = card.aquaUser!!.ghostCard
    async { repo.save(data) }
    return true
}

suspend fun getSummaryFor(repo: GenericUserDataRepo<*>, card: Card): Map<Str, Any>?
{
    val data = async { repo.findByCard(card) } ?: return null
    return mapOf(
        "name" to data.userName,
        "rating" to data.playerRating,
        "lastLogin" to data.lastPlayDate,
    )
}

@Service
class CardGameService(
    val maimai2: Mai2UserDataRepo,
    val chusan: Chu3UserDataRepo,
    val ongeki: icu.samnyan.aqua.sega.ongeki.dao.userdata.UserDataRepository,
    val diva: icu.samnyan.aqua.sega.diva.dao.userdata.PlayerProfileRepository,
) {
    suspend fun migrate(crd: Card, games: List<String>) = async {
        // Migrate data from the card to the user's ghost card
        // An easy migration is to change the UserData card field to the user's ghost card
        games.forEach { game ->
            when (game) {
                "mai2" -> migrateCard(maimai2, crd)
                "chu3" -> migrateCard(chusan, crd)
                "ongeki" -> migrateCard(ongeki, crd)
                // TODO: diva
//                "diva" -> diva.findByPdId(card.extId.toInt()).getOrNull()?.let {
//                    it.pdId = card.aquaUser!!.ghostCard
//                }
            }
        }
    }

    suspend fun getSummary(card: Card) = async { mapOf(
        "mai2" to getSummaryFor(maimai2, card),
        "chu3" to getSummaryFor(chusan, card),
        "ongeki" to getSummaryFor(ongeki, card),
        "diva" to diva.findByPdId(card.extId.toInt()).getOrNull()?.let {
            mapOf(
                "name" to it.playerName,
                "rating" to it.level,
            )
        },
    ) }
}