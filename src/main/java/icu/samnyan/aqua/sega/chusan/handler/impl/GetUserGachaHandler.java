package icu.samnyan.aqua.sega.chusan.handler.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import icu.samnyan.aqua.sega.chusan.handler.BaseHandler;
import icu.samnyan.aqua.sega.chusan.model.userdata.UserGacha;
import icu.samnyan.aqua.sega.chusan.service.UserGachaService;
import icu.samnyan.aqua.sega.util.jackson.BasicMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author samnyan (privateamusement@protonmail.com)
 */
@Component("ChusanGetUserGachaHandler")
public class GetUserGachaHandler implements BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetUserGachaHandler.class);

    private final BasicMapper mapper;

    private final UserGachaService userGachaService;

    @Autowired
    public GetUserGachaHandler(BasicMapper mapper, UserGachaService userGachaService) {
        this.mapper = mapper;
        this.userGachaService = userGachaService;
    }

    @Override
    public String handle(Map<String, Object> request) throws JsonProcessingException {
        String userId = String.valueOf(request.get("userId"));

        List<UserGacha> userGachaList = userGachaService.getByUserId(userId);

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("userId", userId);
        resultMap.put("length", userGachaList.size());
        resultMap.put("userGachaList", userGachaList);

        String json = mapper.write(resultMap);
        logger.info("Response: " + json);
        return json;
    }
}
