package ext

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

// Jackson
val ACCEPTABLE_FALSE = setOf("0", "false", "no", "off", "False", "None", "null")
val ACCEPTABLE_TRUE = setOf("1", "true", "yes", "on", "True")
val JSON_FUZZY_BOOLEAN = SimpleModule().addDeserializer(Boolean::class.java, object : JsonDeserializer<Boolean>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext) = when(parser.text) {
        in ACCEPTABLE_FALSE -> false
        in ACCEPTABLE_TRUE -> true
        else -> 400 - "Invalid boolean value ${parser.text}"
    }
})
val JSON_DATETIME = SimpleModule().addDeserializer(java.time.LocalDateTime::class.java, object : JsonDeserializer<java.time.LocalDateTime>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext) =
        parser.text.asDateTime() ?: (400 - "Invalid date time value ${parser.text}")
})
val JACKSON = jacksonObjectMapper().apply {
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
    setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
    findAndRegisterModules()
    registerModule(JSON_FUZZY_BOOLEAN)
    registerModule(JSON_DATETIME)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE);
}
inline fun <reified T> ObjectMapper.readValue(str: Str) = readValue(str, T::class.java)
// TODO: https://stackoverflow.com/q/78197784/7346633
inline fun <reified T> Str.parseJackson() = if (contains("null")) {
    val map = JACKSON.readValue<MutableMap<String, Any>>(this)
    JACKSON.convertValue(map.recursiveNotNull(), T::class.java)
}
else JACKSON.readValue(this, T::class.java)
fun <T> T.toJson() = JACKSON.writeValueAsString(this)


// KotlinX Serialization
@OptIn(ExperimentalSerializationApi::class)
val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
    namingStrategy = JsonNamingStrategy.SnakeCase
    explicitNulls = false
    coerceInputValues = true
}