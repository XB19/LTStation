package com.ltstation.center.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltstation.center.domain.entity.CmdList;
import com.ltstation.center.domain.repository.CmdListRepository;
import org.springframework.stereotype.Service;

/**
 * File de commandes vers les appareils (table cmd_list), lue par le service de
 * communication des boîtiers. create_time = millisecondes Unix (ConvertDateTimeToInt).
 */
@Service
public class CommandService {

    private final CmdListRepository repo;
    private final ObjectMapper mapper;

    public CommandService(CmdListRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public CmdList enqueue(String cmdType, String deviceNo, Integer stationId, Integer nozId, Object data, int platType) {
        CmdList c = new CmdList();
        c.setDeal(0);
        c.setCmdPlatType(platType);
        c.setDeviceNo(deviceNo);
        c.setCreateTime(System.currentTimeMillis());
        c.setCmdType(cmdType);
        c.setStationId(stationId);
        c.setNozId(nozId);
        try {
            c.setCmdData(data instanceof String s ? s : mapper.writeValueAsString(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return repo.save(c);
    }
}
