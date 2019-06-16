package com.yzx.service;

import com.yzx.model.RoomType;

import java.util.List;
import java.util.Map;

public interface RoomTypeService {
    int addRoomType(RoomType roomType);
    int eidtRoomType(RoomType roomType);
    int deleteRoomType(int id);
    int getTotal(Map<String,Object> map);
    List<RoomType> findList(Map<String,Object> map);
    List<RoomType> findAllRoomeType();
    List<RoomType> findRoomTypesByFloorId(int floorId);
    RoomType findRoomTypeById(int id);
    RoomType findRoomTypeByNameAndHight(String typeName,int hight);
    List<RoomType> findRoomTypeByName(String name);
    void refresh();
}