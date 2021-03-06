package com.mask.ssm.task.compoent;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.mask.ssm.task.utils.HttpUtils;
import com.mask.ssm.task.utils.MyUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Thread.sleep;
@Component
public class StartPingService implements CommandLineRunner {

    @Autowired
    JavaMailSender javaMailSender;

    private String authorization = "";

    private Map<String, Object> consignorBookingAddress = new HashMap<>(); // consignorBookingAddress变量信息

    private Map<String, Object> bookedByAddress = new HashMap<>(); // bookedByAddress变量信息

    private List<String> reference = new ArrayList<>(); // 小提单号

    private int referenceIndex = 0; //已经用到的小提单号下标

    private int days = 0;

    private int maxPrice = 0;

    private Map<String, Object> otherData = new HashMap<>(); // 其他可变的数字

    private String orderedDate = ""; //新的ETD航线时间必须大于这个时间

    private String messageText = ""; //发送邮箱信息正文

    private Map<String, Object> fclQuoteRequest = new HashMap<>();  //航线信息的请求参数

    private Map<String, Object> consigneeBookingAddress = new HashMap<>(); //收货人信息

    private Map<String, Object> notifyBookingAddress = new HashMap<>(); //通知人信息

    private Map<String, Object> brokerageAddress = new HashMap<>(); //货代人信息

    private static Integer time;

    private static List<Map<String, Object>> equipmentRequest = new ArrayList<>();
    private static String equipment;
    private static Integer quantity;
    private static Boolean isNeedLineName;
    private static String vesselName = "";
    private static String voyage = "";
    private static Boolean isNeedSleepTime = true;
    private static Boolean isNeedSupplierName;
    private static String supplierName = "";
    private static Boolean loop = true;
    private static Integer orderSleepTime=0;
    private static Boolean isProxy;
    private static String typeahead = "";
    @Override
    public void run(String... args){
        //,typehead":"NEW YORK"
        String fileUrl = "C:\\spider\\NANSHA-LOSANGELES\\NOETD\\40HQ\\";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        //获取航线信息的请求参数
        Map<String, Object> fclScheduleDataResultMap = JSONObject.parseObject(MyUtils.readJsonFile("data/fclScheduleData.json"));
        fclQuoteRequest = JSONObject.parseObject(fclScheduleDataResultMap.get("fclQuoteRequest").toString());

//        获取文件中的值，头文件
        File file = new File("C:\\spider\\" + "authorization.txt");
        authorization = MyUtils.replaceBlank(MyUtils.txt2String(file));

        // 小提单号
        File file1 = new File(fileUrl + "reference.txt");
        String referenceTemp = null;
        try {
            referenceTemp = FileUtils.readFileToString(file1,"utf8");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        reference = Arrays.asList(referenceTemp.split("\r\n"));

        // 其他可变的数字
        File file2 = new File(fileUrl + "otherData.txt");
        String otherDataTemp = MyUtils.txt2String(file2);
        otherData = JSONObject.parseObject(otherDataTemp);
        days = otherData.get("days") == null ? 0 : (int) otherData.get("days");
//        maxPrice = otherData.get("maxPrice") == null ? 0 :((int) otherData.get("maxPrice"))*((int) otherData.get("quantity"));
        maxPrice = otherData.get("maxPrice") == null ? 0 : ((int) otherData.get("maxPrice"));
        //判断是否需要判断航名和航次
        isNeedLineName = Boolean.parseBoolean(otherData.get("isNeedLineName").toString());
        isNeedSupplierName = Boolean.parseBoolean(otherData.get("isNeedSupplierName").toString());
        if (isNeedLineName) {
            vesselName = otherData.get("vesselName").toString();
            voyage = otherData.get("voyage").toString();
        }
        if (isNeedSupplierName){
            supplierName=otherData.get("supplierName").toString();
        }
        //初始化商品型号和数量
        equipment = otherData.get("equipment").toString();
        quantity = Integer.parseInt(otherData.get("quantity").toString());
        Map<String, Object> map = new HashMap<>(2);
        map.put("equipment", equipment);
        map.put("quantity", quantity);
        equipmentRequest.add(map);
        fclQuoteRequest.put("equipmentRequest", equipmentRequest);
        orderedDate = MyUtils.getNowDate(days, "yyyy-MM-dd");
        typeahead = otherData.get("typeahead").toString();

        orderSleepTime = Integer.parseInt(otherData.get("orderSleepTime").toString());
        isProxy = Boolean.parseBoolean(otherData.get("isProxy").toString());

//        计算实际下单数量
        int successNum = 0;


//        循环请求接口,第一步，请求一次获取航线信息的请求参数接口；第二步循环判断抢单
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("fromCountry", "");
        paramMap.put("from", "");
        paramMap.put("loadType", "fcl");
        paramMap.put("query", otherData.get("from").toString());
        Map<String, Object> fromPort = getPortInfo(paramMap, true);

//        再请求终点港口
        paramMap.put("query", otherData.get("to").toString());
        paramMap.put("from", fromPort.get("matched").toString());
        paramMap.put("fromCountry", fromPort.get("matchedCountry").toString());
        paramMap.put("pickupMode", fromPort.get("mode").toString());
        getPortInfo(paramMap, false);

        while (loop) {
//            随机休眠时间，3-5秒

            try {
                //刷新身份码
                getToken();
               if(isNeedSleepTime){

                   time = Integer.parseInt(otherData.get("sleepTime").toString());
               }
                System.out.print("总共" + reference.size() + "个小提单号，休眠结束时间：" + sdf.format(new Date()) + "查询的是【" + otherData.get("from").toString() + "-" + otherData.get("to").toString() + "】 type:" + equipment + " ETD间隔时间:" + days);
                if(isNeedLineName){
                    System.out.print("【航名】:" + vesselName + "【航次】:" + voyage);
                }else {
                    System.out.print("未指定航名航次");
                }
                if(isNeedSupplierName){
                    System.out.println(" 【航线代码】: " + supplierName);
                }else {
                    System.out.println(" 未指定航线代码 ");
                }
               //        请求接口

                Map<String, Object> fclScheduleWithRatesData = getFclScheduleWithRates();
                if (fclScheduleWithRatesData != null) {
                    System.out.print("【" + otherData.get("from").toString() + "-" + otherData.get("to").toString() + "】 type:" + equipment);
                    System.out.println("查询到所需的航线信息开始下单");
                    //获取收货人和通知人地址信息
                    getNotifyAndConsignee();
//                【获取航线的接口有值】或者【时间差在规定的范围内】
                    while (referenceIndex < reference.size()){
                        String bookId = submitBookings(fclScheduleWithRatesData);

                        if (bookId != null && !"error".equals(bookId)) {
                            // 确定提交，修改状态
                            updateStatus(bookId);
                            referenceIndex++;
                            successNum++;
                        } else if (bookId == null) {
//                    小提单号已存在
                            referenceIndex++;
                        }
                        System.out.print("总共" + reference.size() + "个小提单号，目前是第" + (referenceIndex + 1) + "个，休眠开始时间：" + sdf.format(new Date()) + " 休眠时长: " +time + "查询的是【" + otherData.get("from").toString() + "-" + otherData.get("to").toString() + "】 type:" + equipment);
                        if(isNeedLineName){
                            System.out.println("【航名】:" + vesselName + "【航次】:" + voyage);
                        }else {
                            System.out.println("未指定航名航次");
                        }
                        sleep(orderSleepTime); //暂停时间
                    }
                    loop = false; //结束循环
                }
                    System.out.println("总共" + reference.size() + "个小提单号，休眠开始时间：" + sdf.format(new Date()) + "休眠时长：" + time);
                    sleep(time); //暂停时间
                 }
            catch (NullPointerException nullPointerException){
                System.out.println(nullPointerException);
            }
            catch (Exception e) {
                sendErrorMail("程序出现异常了" + e,"程序停止了，请手动重新运行");
            }
        }
//        发邮箱，运行结束
        sendMail(successNum);

    }


    //    测试方法
    private void testFun() {
    }

    //  获取港口信息接口
    private Map<String, Object> getPortInfo(Map<String, String> paramMap, Boolean type) {
        String url = "https://cetusapi-prod.kontainers.io/trip-ui/api/v1/customer/locations";
        Map<String, String> params = new HashMap<>();
        params.put("Authorization", authorization);
        params.put("Referer", "https://instantquote.one-line.com/");
        String result = HttpUtils.sendGet(url, paramMap, params,false);

//        System.out.println("刷新更新获取自己的信息接口:"+result);
        JSONObject object = JSONObject.parseObject(result);

//        System.out.println("获取港口信息接口:" + object);
        List<Map<String, Object>> location = JSONObject.parseObject(object.get("location").toString(), new TypeReference<List<Map<String, Object>>>() {
        });
        Map<String, Object> data = location.get(0);
        if (type) {
//            请求起始港口信息
            fclQuoteRequest.put("from", data.get("matched"));
            fclQuoteRequest.put("fromCountry", data.get("matchedCountry"));
            fclQuoteRequest.put("fromUNCode", data.get("unCode"));
            if (data.get("iata") == null){
                fclQuoteRequest.remove("fromIata");
            }else {
                fclQuoteRequest.put("fromIata", data.get("iata"));
            }
        } else {
//            请求终点港口信息
            fclQuoteRequest.put("to", data.get("matched"));
            fclQuoteRequest.put("toCountry", data.get("matchedCountry"));
            fclQuoteRequest.put("toUNCode", data.get("unCode"));
            fclQuoteRequest.put("fmc", data.get("fmc"));
            if (data.get("iata") != null) {
                fclQuoteRequest.put("toIata", data.get("iata"));
            }

        }
        return data;

    }

    //    刷新更新获取自己的信息接口
    private void getToken() {

        String url = "https://cetusapi-prod.kontainers.io/tenancy/api/v1/customer/users/self";
        Map<String, String> params = new HashMap<>();
        params.put("Authorization", authorization);
        params.put("Referer", "https://instantquote.one-line.com/");
        String result = HttpUtils.sendGet(url, null, params,false);
        if("".equals(result)){
            System.out.println("getToken result结果为空值");
            return;
        }
//        System.out.println("刷新更新获取自己的信息接口:"+result);
        JSONObject object = JSONObject.parseObject(result);
        if(object == null){
            System.out.println("getToken object为空");
            return;
        }
//        数据处理
        getTokenHandleData(object);
    }

    //    获取航线的接口
    private Map<String, Object> getFclScheduleWithRates() throws ParseException {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        String url = "https://cetusapi-prod.kontainers.io/trip-ui/api/v1/customer/fclScheduleWithRates";
        Map<String, String> params = new HashMap<>();
        params.put("Authorization", authorization);


        messageText = "【从" + fclQuoteRequest.get("from") + "到" + fclQuoteRequest.get("to") + "的航线】";
//        可变数量和型号以及时间
        fclQuoteRequest.put("schedulesAfterDate", MyUtils.getNowDate(7, "yyyy-MM-dd"));


        Map<String, Object> fclScheduleDataResultMap = new HashMap<>();
        fclScheduleDataResultMap.put("fclQuoteRequest", fclQuoteRequest);
        String result = HttpUtils.sendPost(url, fclScheduleDataResultMap, params,false);

        if("".equals(result)){
            System.out.println("获取航线接口的数据为空");
            return null;
        }

        JSONObject object = JSONObject.parseObject(result);
        if (object == null){
            System.out.println("getFclScheduleWithRates  Object为空");
            return  null;
        }
        if (object.get("multiLegScheduleWithRate") != null) {
//            有航线,取最新的一条航线
            List<Map<String, Object>> multiLegScheduleWithRateList = JSONObject.parseObject(object.get("multiLegScheduleWithRate").toString(), new TypeReference<List<Map<String, Object>>>() {
            });
            List<Map<String, Object>> supplierSummaries = JSONObject.parseObject(object.get("supplierSummaries").toString(), new TypeReference<List<Map<String, Object>>>() {
            });
            if (multiLegScheduleWithRateList.size() > 0) {
//               循环，取最新的(etd最大的)，以及判断与当前时间的差在11天以上（目前测试保证在15+7天）
                Map<String, Object> multiLegScheduleWithRate = null;

                for (Map<String, Object> temp : multiLegScheduleWithRateList) {
                    //  获取变量
                    List<Map<String, Object>> legs = JSONObject.parseObject(temp.get("legs").toString(), new TypeReference<List<Map<String, Object>>>() {
                    });

//                   未显示的航班，过滤掉
                    List<Map<String, Object>> issues = JSONObject.parseObject(legs.get(0).get("issues").toString(), new TypeReference<List<Map<String, Object>>>() {
                    });
                    if (issues.size() > 0) {
                        continue;
                    }
//                   禁止下单的航班过滤掉
                    if (Boolean.parseBoolean(legs.get(0).get("isSoldOut").toString())) {
                        continue;
                    }

                    // 如果航线的终点是 new york 需要制定 其他都不需要
                    if("NEW YORK, NY".equals(legs.get(0).get("to").toString()) && "NINGBO, ZHEJIANG".equals(legs.get(0).get("from").toString())){
                        if (!"EAST COAST 2".equals(legs.get(0).get("supplierName").toString())) {
                            continue;
                        }
                    }
                    if(isNeedSupplierName){
                        if(!supplierName.equals(legs.get(0).get("supplierName").toString())){
                            continue;
                        }
                    }
                    if (isNeedLineName) {
                        if ((!vesselName.equals(legs.get(0).get("vesselName"))) && !(voyage.equals(legs.get(0).get("voyage")))) {
                            continue;
                        }
                    }
//                   判断航线条件
                    if (days != 0 && maxPrice != 0) {
//                       即判断时间，也判断价格
                        if (MyUtils.dateCompare(dateFormat.parse(orderedDate), dateFormat.parse(legs.get(0).get("etd").toString())) && Float.parseFloat(temp.get("totalCost").toString()) < maxPrice) {
                            multiLegScheduleWithRate = legs.get(0);
                        }
                    } else if (days != 0) {
                        //                  判断时间是否大于orderedDate起始时间
                        if (MyUtils.dateCompare(dateFormat.parse(orderedDate), dateFormat.parse(legs.get(0).get("etd").toString()))) {
                            multiLegScheduleWithRate = legs.get(0);
                        }
                    } else if (maxPrice != 0) {
                        //                       根据钱作为判断,小于满足条件的价格
                        if (Float.parseFloat(temp.get("totalCost").toString()) < maxPrice) {
                            multiLegScheduleWithRate = legs.get(0);
                        }
                    }else {
                        multiLegScheduleWithRate = legs.get(0);
                    }

                }

                if (multiLegScheduleWithRate == null) {
                    String printText = "";
                    if (days != 0) {
                        printText = "【目前最新ETD时间】：" + orderedDate + ";";
                    }
                    if (maxPrice != 0) {
                        printText = printText + "【可接受金额必须小于】：" + maxPrice + ";";
                    }
//                    System.out.println(printText + "【获取航线的接口数据】" + result);
                    return null;
                }
                else {
                    isNeedSleepTime = false;
                    time = 0;
                }
//               保留时间
//               orderedDate = multiLegScheduleWithRate.get("etd").toString();
                return fclScheduleWithRatesHandleData(multiLegScheduleWithRate, supplierSummaries);
            }
        }
        System.out.println("【获取航线的接口】失败或者暂无数据");
        return null;
    }

    // 预提交
    private String submitBookings(Map<String, Object> fclScheduleWithRatesData) throws IOException {
        String url = "https://cetusapi-prod.kontainers.io/booking/api/v1/customer/bookings";
        Map<String, String> params = new HashMap<>();
        params.put("Authorization", authorization);

//        请求数据处理
        Map<String, Object> resultMap = submitBookingsData(fclScheduleWithRatesData);
        System.out.println("【预提交】请求数据处理:" + resultMap);
//        return null;
        String result = HttpUtils.sendPost(url, resultMap, params,isProxy);

        JSONObject object = JSONObject.parseObject(result);
        if (object.get("booking") != null) {
            Map<String, Object> info = JSONObject.parseObject(object.get("booking").toString());
            System.out.println("【预提交成功】:" + info.get("id"));
            return info.get("id").toString();
        } else {
            List<Map<String, Object>> errors = JSONObject.parseObject(object.get("errors").toString(), new TypeReference<List<Map<String, Object>>>() {
            });
            if ("422".equals(errors.get(0).get("status"))) {
                System.out.println("【小提单号已存在】:" + result);
                return null;
            } else {
                System.out.println("【预提交失败】:" + result);
                return "error";
            }
        }
    }

    // 确定提交，修改状态
    private void updateStatus(String bookId) {

        String url = "https://cetusapi-prod.kontainers.io/booking/api/v1/customer/booking/tick/" + bookId;
        Map<String, String> params = new HashMap<>();
        params.put("Authorization", authorization);

        Map<String, Object> resultMap = new HashMap<>();
        String result = HttpUtils.sendPut(url, resultMap, params,isProxy);

        System.out.println("【确定提交，修改状态】:" + result);
    }

    // 预提交接口提交的数据处理
    Map<String, Object> submitBookingsData(Map<String, Object> fclScheduleWithRatesData) throws IOException {

        Map<String, Object> booking = JSONObject.parseObject(MyUtils.readJsonFile("data/submitBookData.json"));
        Map<String, Object> info = JSONObject.parseObject(booking.get("booking").toString());

        info.put("fmc", fclQuoteRequest.get("fmc"));
        List<Map<String, Object>> referencesTemp;
        if("NANSHA, GUANGDONG".equals(otherData.get("from").toString())||"YANTIAN, GUANGDONG".equals(otherData.get("from").toString()) || "XIAMEN, FUJIAN".equals(otherData.get("from").toString())){
            //只有Yantian和xiamen 是 填空 其他都要填
            referencesTemp = new ArrayList<>();
        }else {
            //可变参数，小提单号
            referencesTemp=JSONObject.parseObject(info.get("references").toString(), new TypeReference<List<Map<String, Object>>>() {});
            referencesTemp.get(0).put("reference", reference.get(referenceIndex));
        }

        info.put("references", referencesTemp);
        info.put("customerReference", reference.get(referenceIndex));

        //可变参数，型号
        List<Map<String , Object>> bookingItems =  JSONObject.parseObject(info.get("bookingItems").toString(), new TypeReference<List<Map<String, Object>>>() {});
        bookingItems.get(0).put("containerType",equipment);
//        判断数量，是否大于1,一票多个高柜
        if (quantity > 1){
            for (int i = 1; i < quantity; i++){
                bookingItems.add(bookingItems.get(0));
            }
        }
        info.put("bookingItems",bookingItems);
        info.put("bookingCosts", fclScheduleWithRatesData.get("bookingCosts"));
        info.put("logisticsDetails", fclScheduleWithRatesData.get("logisticsDetails"));


        info.put("consignorBookingAddress", consignorBookingAddress);

        info.put("bookedByAddress", bookedByAddress);

        info.put("consigneeBookingAddress", consigneeBookingAddress);
        info.put("notifyBookingAddress", notifyBookingAddress);
        if(!"NINGBO, ZHEJIANG".equals(otherData.get("from").toString())){
            info.put("brokerageAddress",brokerageAddress);
        }
        Map<String, Object> resultMap = new HashMap<>();
        HttpUtils.writeMsg("C:\\spider\\log.txt",info.toString());
        resultMap.put("booking", info);
        return resultMap;
    }

    //    获取航线的接口返回的数据处理
    private Map<String, Object> fclScheduleWithRatesHandleData(Map<String, Object> multiLegScheduleWithRate, List<Map<String, Object>> supplierSummaries) {

//        1、收集logisticsDetails里面的信息，完成预提交接口
        List<Map<String, Object>> logisticsDetails = new ArrayList<>();
        Map<String, Object> logisticsDetailsTemp = new HashMap<>();
        Map<String, Object> seaLegData = new HashMap<>();
        seaLegData.put("billType", "negotiable_received");
        seaLegData.put("lloydsNumber", multiLegScheduleWithRate.get("lloydsNumber"));
        seaLegData.put("vesselImo", multiLegScheduleWithRate.get("vesselImo"));

        logisticsDetailsTemp.put("seaLegData", seaLegData);
        logisticsDetailsTemp.put("eta", multiLegScheduleWithRate.get("eta"));
        logisticsDetailsTemp.put("etd", multiLegScheduleWithRate.get("etd"));
        logisticsDetailsTemp.put("vgmCutOffTime", multiLegScheduleWithRate.get("vgmCutOffTime"));
        logisticsDetailsTemp.put("siDocCutOffTime", multiLegScheduleWithRate.get("siDocCutOffTime"));
        logisticsDetailsTemp.put("latestPortArrivalTime", multiLegScheduleWithRate.get("latestPortArrivalTime"));
        logisticsDetailsTemp.put("fromLocation", multiLegScheduleWithRate.get("from"));
        logisticsDetailsTemp.put("fromCountry", multiLegScheduleWithRate.get("fromCountry"));
        logisticsDetailsTemp.put("fromIata", multiLegScheduleWithRate.get("fromIata"));
        logisticsDetailsTemp.put("fromUNCode", multiLegScheduleWithRate.get("fromUNCode"));
        logisticsDetailsTemp.put("toLocation", multiLegScheduleWithRate.get("to"));
        logisticsDetailsTemp.put("toCountry", multiLegScheduleWithRate.get("toCountry"));
        logisticsDetailsTemp.put("toUNCode", multiLegScheduleWithRate.get("toUNCode"));
        logisticsDetailsTemp.put("machineName", multiLegScheduleWithRate.get("vesselName"));
        logisticsDetailsTemp.put("voyageNumber", multiLegScheduleWithRate.get("voyage"));
        logisticsDetailsTemp.put("mode", multiLegScheduleWithRate.get("mode"));
        logisticsDetailsTemp.put("toIata", multiLegScheduleWithRate.get("toIata"));

        for (Map<String, Object> temp : supplierSummaries) {
            if (multiLegScheduleWithRate.get("supplierName").equals(temp.get("fullName"))) {
                logisticsDetailsTemp.put("supplier", temp.get("scac"));
            }
        }
        logisticsDetails.add(logisticsDetailsTemp);

//        2、收集bookingCosts
        List<Map<String, Object>> rates = JSONObject.parseObject(multiLegScheduleWithRate.get("rates").toString(), new TypeReference<List<Map<String, Object>>>() {
        });
        for (Map<String, Object> ratesTemp : rates) {
            ratesTemp.remove("id");
            ratesTemp.put("description", ratesTemp.get("desc"));
            ratesTemp.remove("desc");
            ratesTemp.put("oriAmount", ratesTemp.get("originAmount"));
            ratesTemp.remove("originAmount");
            ratesTemp.put("paymentTerm", "collect");
        }
//        3、放入到一个map中
        Map<String, Object> resultMap = new HashMap<>(2);
        resultMap.put("bookingCosts", rates);
        resultMap.put("logisticsDetails", logisticsDetails);
        System.out.println("【获取航线的接口】成功");
        return resultMap;

    }

    //    刷新更新获取自己的信息接口返回的数据处理
    private void getTokenHandleData(JSONObject object) {
        if (object == null){
            return;
        }
        Map<String, Object> addressInfo = new HashMap<>();
        JSONObject info = JSONObject.parseObject(object.get("user").toString());
        addressInfo.put("lastName", info.get("lastName").toString());
        addressInfo.put("firstName", info.get("firstName").toString());
        addressInfo.put("email", info.get("email").toString());


        JSONObject company = JSONObject.parseObject(info.get("company").toString());
        String addressText = company.get("name").toString();
        addressInfo.put("companyRole", company.get("role").toString());
        addressInfo.put("companyName", addressText);
        addressInfo.put("customerCode", company.get("companyCode").toString());
        JSONObject companyAddress = company.getJSONArray("addresses").getJSONObject(0);
//        JSONObject companyAddress = JSONObject.parseObject(company.get("addresses").toString());
        addressInfo.put("address1", companyAddress.get("address1").toString());
        addressInfo.put("address2", companyAddress.get("address2").toString());
        addressInfo.put("cityTown", companyAddress.get("cityTown").toString());
        addressInfo.put("country", companyAddress.get("country").toString());
        addressInfo.put("vatNo", companyAddress.get("vatNo").toString());
        addressInfo.put("postCode", companyAddress.get("postCode").toString());
        addressText = addressText + "," + companyAddress.get("address1").toString() + "," + companyAddress.get("address2").toString() + "," + companyAddress.get("cityTown").toString() + "," + companyAddress.get("postCode").toString() + ",CN";

        addressInfo.put("addressText", addressText);

        consignorBookingAddress.putAll(addressInfo);

        bookedByAddress.putAll(addressInfo);
        brokerageAddress.putAll(addressInfo);
        authorization = info.get("token").toString();
        System.out.println("更新authorization");

//        System.out.println("刷新更新获取自己的信息接口:" + authorization);
    }

    private void getNotifyAndConsignee() {
        String url = "https://cetusapi-prod.kontainers.io/tenancy/api/v1/customer/addresses";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Referer", "https://instantquote.one-line.com/");
        Map<String, String> params = new HashMap<>();
        params.put("page", "0");
        params.put("size", "25");
        params.put("typeahead", typeahead); //NEW YORK
        String response = HttpUtils.sendGet(url, params, headers,false);
        JSONObject jsonObject = JSONObject.parseObject(response);
        JSONArray address = jsonObject.getJSONArray("address");
        JSONObject j1 = JSONObject.parseObject(address.get(0).toString());

        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("address1", j1.get("address1").toString());
        addressMap.put("addressText", j1.get("singleLine").toString());
//        addressMap.put("addressType", "notifyBookingAddress");
        addressMap.put("cityTown", j1.get("cityTown").toString());
        addressMap.put("companyName", j1.get("companyName").toString());
        addressMap.put("country", j1.get("country").toString());
        addressMap.put("email", j1.get("email").toString());
        addressMap.put("firstName", j1.get("firstName").toString());
        addressMap.put("lastName", j1.get("lastName").toString());
        addressMap.put("phone", j1.get("phone").toString());
        addressMap.put("vatNo", j1.get("vatNo").toString());

        notifyBookingAddress.putAll(addressMap);
        consigneeBookingAddress.putAll(addressMap);
    }

    //发送邮箱
    private void sendMail(int successNum) {
        // 构建一个邮件对象
        SimpleMailMessage message = new SimpleMailMessage();
        // 设置邮件主题
        message.setSubject(messageText + "抢单成功");
        // 设置邮件发送者，这个跟application.yml中设置的要一致
        message.setFrom("1059308740@qq.com");
        // 设置邮件接收者，可以有多个接收者，中间用逗号隔开，以下类似
        // message.setTo("10*****16@qq.com","12****32*qq.com");
//        message.setTo("1973432033@qq.com","Zhibo_Tang@zjou.edu.cn","771829811@qq.com"); one-ebooking@nb-hj.com 公司邮箱
        message.setTo("1059308740@qq.com","1973432033@qq.com","1610531743@qq.com","one-ebooking@nb-hj.com");
        // 设置邮件发送日期
        message.setSentDate(new Date());
        // 设置邮件的正文
        if (referenceIndex > successNum) {
//            代表有小提单号已被使用过
            messageText = messageText + "，实际成功下了" + successNum + "单，其中小提单号有被使用过而下单失败" +
                    "下单的小提单号：【" + String.join("，", reference) + "】";
        } else {
            if (isNeedLineName){
                messageText = messageText + "，成功下了" + successNum + "单。" + "柜子型号：" + equipment+ "航名:" + vesselName + "航次:" + voyage + " ETD间隔时间:"  + days +
                        "下单的小提单号：【" + String.join("，", reference) + "】";
            }else {
                if(isNeedSupplierName){
                    messageText = messageText + "，成功下了" + successNum + "单。" + "柜子型号：" + equipment+ " ETD间隔时间:" + days + " 航线代码" + supplierName +
                            "下单的小提单号：【" + String.join("，", reference) + "】";
                }else {
                    messageText = messageText + "，成功下了" + successNum + "单。" + "柜子型号：" + equipment+ " ETD间隔时间:" + days +
                            "下单的小提单号：【" + String.join("，", reference) + "】";
                }

            }


        }

        message.setText(messageText);
        // 发送邮件
        javaMailSender.send(message);
    }

    //发送邮箱
    private void sendErrorMail(String errorMessage,String title) {
        // 构建一个邮件对象
        SimpleMailMessage message = new SimpleMailMessage();
        // 设置邮件主题
        message.setSubject(title);
        // 设置邮件发送者，这个跟application.yml中设置的要一致
        message.setFrom("1059308740@qq.com");
        // 设置邮件接收者，可以有多个接收者，中间用逗号隔开，以下类似
        // message.setTo("10*****16@qq.com","12****32*qq.com");
//        message.setTo("1973432033@qq.com","Zhibo_Tang@zjou.edu.cn","771829811@qq.com");
        message.setTo("1059308740@qq.com");
        // 设置邮件发送日期
        message.setSentDate(new Date());

        message.setText(messageText + errorMessage);
        // 发送邮件
        javaMailSender.send(message);
    }
}
