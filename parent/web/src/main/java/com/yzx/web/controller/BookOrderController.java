package com.yzx.web.controller;

import com.yzx.model.Account;
import com.yzx.model.BlackList;
import com.yzx.model.BookOrder;
import com.yzx.model.RoomType;
import com.yzx.model.admin.Log;
import com.yzx.model.admin.Page;
import com.yzx.model.admin.Room;
import com.yzx.service.AccountService;
import com.yzx.service.BlackListService;
import com.yzx.service.BookOrderService;
import com.yzx.service.RoomTypeService;
import com.yzx.service.admin.LogService;
import com.yzx.service.admin.RoomService;
import com.yzx.util.CheckId;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("admin/bookOrder")
public class BookOrderController {

    @Autowired
    private BookOrderService bookOrderService;
    @Autowired
    private RoomTypeService roomTypeService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private BlackListService blackListService;
    @Autowired
    private LogService logService;

    @RequestMapping(value = "list",method = RequestMethod.GET)
    public String list(HttpServletRequest request){
        List<RoomType> allRoomTypes=roomTypeService.findAllRoomeType();
        Set<String> roomTypeNames=new HashSet<>();
        for(RoomType r:allRoomTypes){
            if(r.getStatus()==RoomType.NOT_LIVE){
                continue;
            }
            roomTypeNames.add(r.getName());
        }
        request.getSession().setAttribute("roomTypeNames",roomTypeNames);
        return "admin/bookOrder/list";
    }

    @RequestMapping("add")
    @ResponseBody
    public Map<String,Object> add(BookOrder bookOrder,String arriveTime,String leaveTime,String roomTypeName,Integer hight,String accountPhone){
        Map<String,Object> ret=new HashMap<>();

        Account account=accountService.findAccountByPhoneNum(accountPhone);

        Map<String,Object> res=CheckId.getRequest1(bookOrder.getIdCard(),bookOrder.getName());
        if(res.get("type").equals("error")){
            return res;
        }

        if(!bindAccountPhone(accountPhone,bookOrder,ret)){
            return ret;
        }

        if(!IsContinueByDateFormat(ret,arriveTime,leaveTime).get("type").equals("success")){
            return ret;
        }

        RoomType roomType=getRoomType(roomTypeName,hight);
        setBookOrder(bookOrder,(Date)ret.get("arriveDate"),(Date)ret.get("leaveDate"),roomType);

        if(!makeRoom_0_to_1(roomType,ret,bookOrder)){
            return ret;
        }

        if(bookOrderService.addBookOrder(bookOrder)<=0){
            ret.put("type","error");
            ret.put("msg","添加失败 请联系管理员");
            logService.addLog(Log.SYSTEM,"预定失败","手机号为"+account.getPhoneNum()+"预定时操作数小于1");
        }else {
            ret.put("type","success");
            logService.addLog(Log.BUSSINESS,"预定成功","手机号为"+account.getPhoneNum()+"预定成功");
        }
        return ret;
    }

    @RequestMapping("update")
    @ResponseBody
    public Map<String,Object> update(BookOrder bookOrder,String accountPhone,String arriveTime,String leaveTime,int oldRoomTypeId,String roomTypeName,Integer hight){
        Map<String,Object> ret=new HashMap<>();

        if(bookOrder.getStatus()!=BookOrder.IN_BOOK){
            ret.put("type","error");
            ret.put("msg","只有预定订单可以修改");
            return ret;
        }

        Map<String,Object> res=CheckId.getRequest1(bookOrder.getIdCard(),bookOrder.getName());
        if(res.get("type").equals("error")){
            return res;
        }

        if(!bindAccountPhone(accountPhone,bookOrder,ret)){
            return ret;
        }

        if(!IsContinueByDateFormat(ret,arriveTime,leaveTime).get("type").equals("success")){
            return ret;
        }

        RoomType newRoomType=getRoomType(roomTypeName,hight);
        RoomType oldRoomType=roomTypeService.findRoomTypeById(oldRoomTypeId);
        setBookOrder(bookOrder,(Date)ret.get("arriveDate"),(Date)ret.get("leaveDate"),newRoomType);

        if(oldRoomTypeId!=newRoomType.getId()){
            if(!makeRoom_0_to_1(newRoomType,ret,bookOrder)){
                return ret;
            }
            makeRoom_1_to_0(oldRoomType);
        }
        if(bookOrderService.eidtBookOrder(bookOrder)<=0){
            ret.put("type","error");
            ret.put("msg","修改失败 请联系管理员");
            logService.addLog(Log.SYSTEM,"预定修改失败","手机号为"+accountPhone+"预定修改操作数小于1");

        }else{
            ret.put("type","success");
            logService.addLog(Log.BUSSINESS,"预定成功","手机号为"+accountPhone+"预定修改成功");
        }
        return ret;
    }

    @RequestMapping("delete")
    @ResponseBody
    public Map<String,String> delete(int [] id){
        Map<String,String> ret=new HashMap<>();
        try{
            for(int i=0;i<id.length;i++){
                BookOrder bookOrder=bookOrderService.findBookOrderById(id[i]);
                if(bookOrder.getStatus()==BookOrder.IN_ARRIVED){
                    ret.put("type", "error");
                    ret.put("msg", "删除停止 存在正在入住的订单");
                    return ret;
                }

                int roomTypeId=bookOrder.getRoomTypeId();
                if(bookOrderService.deleteBookOrder(id[i])<=0) {
                    ret.put("type", "error");
                    ret.put("msg", "删除中出错 请联系管理员");
                    logService.addLog(Log.SYSTEM,"预定删除失败","预定删除操作数小于1");
                    return ret;
                }else {
                    RoomType roomType=roomTypeService.findRoomTypeById(roomTypeId);
                    if(bookOrder.getStatus()==BookOrder.IN_BOOK){
                        makeRoom_1_to_0(roomType);
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            ret.put("type", "error");
            ret.put("msg", "该条订单正在入住 等入住结束才可以删除！");
            logService.addLog(Log.BUSSINESS,"预定删除失败","房间正在入住");
            return ret;
        }
        ret.put("type","success");
        return ret;
    }

    @RequestMapping(value="list",method = RequestMethod.POST)//搜索的时候的参数名
    @ResponseBody
    public Map<String,Object> findList(Page page,Integer status,String arriveTime,String leaveTime,
                                       @RequestParam(value = "accountPhone",defaultValue = "",required = false)String accountPhone,
                                       @RequestParam(value = "name",defaultValue = "",required = false)String name,
                                       @RequestParam(value = "phoneNum",defaultValue = "",required = false)String phoneNum

    ) throws ParseException {
        Map<String,Object> ret=new HashMap<>();
        Map<String,Object> queryMap=new HashMap<>();

        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd");
        queryMap.put("accountPhone",accountPhone);
        queryMap.put("name",name);
        queryMap.put("phoneNum",phoneNum);
        if (!StringUtils.isEmpty(arriveTime)){
            queryMap.put("arriveDate",format.parse(arriveTime));
        }else {
            queryMap.put("arriveDate",arriveTime);
        }
        if (!StringUtils.isEmpty(leaveTime)){
            queryMap.put("leaveDate",format.parse(leaveTime));
        }else {
            queryMap.put("leaveDate",leaveTime);
        }
        queryMap.put("status",status);
        queryMap.put("pageSize",page.getRows());
        queryMap.put("offset",page.getOffset());
        System.out.println(queryMap);

        ret.put("rows",bookOrderService.findList(queryMap));
        ret.put("total",bookOrderService.getTotal(queryMap));
        return ret;
    }


    @RequestMapping("findbookOrderById")
    @ResponseBody
    public BookOrder findbookOrderById(int id){
        return bookOrderService.findBookOrderById(id);
    }

    boolean checkArriveAndLeaveTime(Date arrive,Date leave) throws ParseException {
        SimpleDateFormat formater=new SimpleDateFormat("yyyy-MM-dd");
        Date now_day=formater.parse(formater.format(new Date()));
        if(now_day.compareTo(arrive)!=1) {
            if(arrive.compareTo(leave)==-1){
                return true;
            }
        }
        return false;
    }

    boolean bindAccountPhone(String accountPhone,BookOrder bookOrder,Map<String,Object>ret){
        try{
            Account account=accountService.findAccountByPhoneNum(accountPhone);
            bookOrder.setAccountId(account.getId());

            List<BlackList> blackLists=blackListService.findAll();
            for(BlackList blackList:blackLists){
                if(account.getId()==blackList.getAccountId()){
                    ret.put("type","error");
                    ret.put("msg","添加失败，下单账户现在处于冻结状态，可能由于违约次数超过上限，一月3次后冻结一个月，达20次将永久冻结，您现在月违约："+account.getMonthBreakTimes()+"，共违约："+account.getSumBreakTimes());
                    logService.addLog(Log.BUSSINESS,"预定失败","手机号为"+account.getPhoneNum()+"预定失败，因为已被加黑");
                    return false;
                }
            }
        }catch (Exception e){
            ret.put("type","error");
            ret.put("msg","不存在改手机号绑定的账号");
            return false;
        }
        return true;
    }

    public Map<String,Object> IsContinueByDateFormat(Map<String,Object> ret,String arriveTime,String leaveTime){
        Date arriveDate;
        Date leaveDate;
        try{
            SimpleDateFormat formater=new SimpleDateFormat("yyyy-MM-dd");
            arriveDate=formater.parse(arriveTime);
            leaveDate=formater.parse(leaveTime);

            if(!checkArriveAndLeaveTime(arriveDate,leaveDate)){
                ret.put("type","error");
                ret.put("msg","日期填写不合适 请修改");
                return ret;
            }
        }catch (Exception e){
            ret.put("type","error");
            ret.put("msg","日期格式转化不正确 请按格式填写");
            return ret;
        }
        ret.put("type","success");
        ret.put("arriveDate",arriveDate);
        ret.put("leaveDate",leaveDate);
        return ret;
    }

    public RoomType getRoomType(String roomTypeName,Integer hight){
        RoomType roomType=null;
        if(hight.intValue() !=-1){
            roomType=roomTypeService.findRoomTypeByNameAndHight(roomTypeName,hight);
        }else {
            List<RoomType> roomTypeList=roomTypeService.findRoomTypeByName(roomTypeName);
            int size=roomTypeList.size();
            Random random=new Random();
            roomType=roomTypeList.get(random.nextInt(size));
        }
        return roomType;
    }

    public void setBookOrder(BookOrder bookOrder,Date arriveDate,Date leaveDate,RoomType roomType){
        bookOrder.setArriveDate(arriveDate);
        bookOrder.setLeaveDate(leaveDate);
        bookOrder.setCreateTime(new Date());
        bookOrder.setStatus(0);
        bookOrder.setRoomTypeId(roomType.getId());
    }

    public synchronized boolean makeRoom_0_to_1(RoomType roomType,Map<String,Object>ret,BookOrder bookOrder){
        List<Room> rooms=roomService.findRoomByTypeIdAndStatus(roomType.getId(),0);

        int size=rooms.size();
        Random random=new Random();
        try{
            SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy/MM/dd");
            Room room=rooms.get(random.nextInt(size));
            room.setStatus(1);
          //  room.setRemark("初步分配信息：<li>预定账号："+accountService.findAccountById(bookOrder.getAccountId()).getPhoneNum()+"</li><li>入住者："+bookOrder.getName()+"</li><li>联系电话："+bookOrder.getPhoneNum()+"</li><li>入住时间："+dateFormat.format(bookOrder.getArriveDate())+"--"+dateFormat.format(bookOrder.getLeaveDate())+"</li>");
            roomService.eidtRoom(room);
        }catch (Exception e){
            ret.put("type", "error");
            ret.put("msg", "预定失败 已经没有剩房");
            return false;
        }
        roomType.setBookNum(roomType.getBookNum()+1);
        roomTypeService.eidtRoomType(roomType);
        return true;
    }

    public void makeRoom_1_to_0(RoomType roomType){
        roomType.setBookNum(roomType.getBookNum()-1);
        if(roomType.getAvilableNum()>0){
            roomType.setStatus(1);
        }
        roomTypeService.eidtRoomType(roomType);
        List<Room> rooms=roomService.findRoomByTypeIdAndStatus(roomType.getId(),1);

        Random random=new Random();
        int size=rooms.size();
        Room room=rooms.get(random.nextInt(size));
        room.setStatus(0);
        room.setRemark("");
        roomService.eidtRoom(room);
    }

}
