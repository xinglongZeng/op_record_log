package com.zxl.op_record_log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.format.datetime.standard.DateTimeContextHolder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Aspect
public class OpRecordAspect {

    private CommonAuthContext authContext;

    private OpRecordProcessConfig config;

    private OpRecordPersistenceService persistenceService;

    /**
     * 表达式语法错误
     */
    public static final String EXP_SYNTAX_ERR="Does not conform to expression syntax!";

    /**
     * 已经解析成AST的缓存
     */
    private static final Map<Method, Map<String,DesensitizationField>> APPROVED_EXP=new HashMap<>();

    private static final Map<Class<?>,Map<String,Field>> CACHE_FIELD=new HashMap<>();



    public OpRecordAspect(CommonAuthContext authContext,OpRecordProcessConfig config ,OpRecordPersistenceService persistenceService) throws Exception {

        if(config==null){
            throw new Exception("OpRecordProcessConfig is null !");
        }

        if(config.isPersistenceFlg() && persistenceService == null ){
            throw new Exception("OpRecordPersistenceService is null !");
        }

        this.authContext = authContext;
        this.config = config;
        this.persistenceService = persistenceService;
        log.debug("OpRecordAspect Init success!");
    }



    @Pointcut("@annotation(com.zxl.op_record_log.OpRecordAnnotation)")
    public void point() {

    }


    @Around("point()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable{
        long startTime = System.currentTimeMillis();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        Signature signature = pjp.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();

        OpRecordAnnotation annotation = method.getAnnotation(OpRecordAnnotation.class);

        //  ------  组装操作日志的字段 ------
        OpRecordModelDto dto = new OpRecordModelDto();

        // 操作人账号
        dto.setOpAccount(authContext.getAccountFromRequest());
        // 操作时间
        dto.setOpTime(new Date());
        // 请求的功能
        dto.setReqFunc(method.getAnnotation(ApiOperation.class).value());
        // 请求的url
        dto.setReqUrl(request.getRequestURI());
        // 请求的方式
        dto.setReqMethod(request.getMethod());

        // 请求的报文
        dto.setReqData(getReqDataJsonStr(annotation, method ,pjp.getArgs()));

        // 执行被调用的函数，获取执行结果
        Object result = pjp.proceed();
        // 响应报文
        dto.setRespData(getRespDataJsonStr(annotation,method,result));
        // todo:响应状态
        // dto.setSuccess(result.getSuccess());
        // 执行耗时
        dto.setElapsedTime(System.currentTimeMillis()-startTime);

        // 打印日志
        if(config.isLogFlg()){
            log.info(" 接口调用记录 : {}",dto);
        }

        // 进行持久化
        if(config.isPersistenceFlg()){
            persistenceService.persistence(dto);
        }

        return result;

    }


    /**
     * 构建ast
     *  str最多分3类表达式：
     *      1. 普通模式(只有字段名）
     *      2. 点模式(有"."的结构,包括多个"."以及"."与"[]"混合)
     *      3. 括号模式（ 有"[]"的）
     *
     *
     */
    public static DesensitizationField buildAst(Method method,String exp, boolean useCache){

        if(StringUtils.isEmpty(exp)){
            return null;
        }

        DesensitizationField ast = null;

        if(useCache){
            ast = getAstFromCache(method,exp);
        }

        if(ast!=null){
            return ast;
        }

        // 点模式
        if(exp.contains(".")){
            ast= buildAstByPoint(exp);
        // 括号模式
        } else if (exp.contains("[") && exp.contains("]")) {
            ast = buildAstByBrackets(exp);
        }else {
            //普通模式
            DesensitizationField field = new DesensitizationField();
            field.setKeyName(exp);
            ast = field;
        }

        return ast;
    }

    public static List<String> parseExpToLink(String exp){
        List<String> link = new LinkedList<>();
        if(StringUtils.isEmpty(exp)){
            return link;
        }

        // 点模式
        if(exp.contains(".")){
            int point_idx=exp.indexOf(".");
            link.addAll(parseExpToLink(exp.substring(0,point_idx)));
            link.addAll(parseExpToLink(exp.substring(point_idx+1)));
            // 括号模式
        } else if (exp.contains("[") && exp.contains("]")) {
            int left_idx = exp.indexOf("[");
            link.addAll(parseExpToLink(exp.substring(0,left_idx)));
            int right_idx = exp.indexOf("]");
            link.addAll(parseExpToLink(exp.substring(right_idx+1)));

        }else {
            //普通模式
            link.add(exp);
        }

        return link;
    }


    private static DesensitizationField getAstFromCache(Method method, String exp) {
        Map<String,DesensitizationField> all = APPROVED_EXP.get(method);

        if( all==null){
            return null;
        }

        List<String> link = OpRecordAspect.parseExpToLink(exp);

        DesensitizationField existAst = all.get(link.get(0));

        if(existAst==null){
            return null;
        }

        DesensitizationField node=existAst;
        for(int i=1;i<link.size();i++){
            if(node.getFieldMap().containsKey(link.get(i))){
                node = node.getFieldMap().get(link.get(i));
            }else {
                return null;
            }
        }

        return existAst;
    }




    /**
     * 解析括号模式的字符串为DesensitizationField
     * @param str 括号模式
     * @return DesensitizationField
     */
    private static DesensitizationField buildAstByBrackets(String str) {

        // 获取到第一个左括号和右括号的index
        int left_idx = str.indexOf("[");

        // 左括号左边的字符串为parent的key
        String key = str.substring(0,left_idx);

        // 第一个右括号的位置
        int right_idx = str.indexOf("]");

        // 左右括号之间的为括号中间的表达式的集合,并且多个表达式用逗号分隔开
        String[] exps = str.substring(left_idx+1,right_idx).split(",");

        DesensitizationField parent = DesensitizationField.getArrayEntity(key, exps);

        // 处理右括号右边的剩余的字符串
        DesensitizationField sub =buildAst(null,str.substring(right_idx+1),false);

        if(sub!=null){
            if(parent.getFieldMap()==null){
                parent.setFieldMap(new HashMap<>());
            }
            parent.getFieldMap().put(sub.getKeyName(),sub);
        }

        return parent;
    }

    /**
     * 解析点模式的字符串为DesensitizationField
     * @param str 点模式字符串
     * @return DesensitizationField
     */
    private static DesensitizationField buildAstByPoint(String str) {
        // 获取到第一个"."的位置，然后进行分割
        int pointIndex = str.indexOf(".");

        DesensitizationField parent = buildAst(null,str.substring(0,pointIndex),false);

        DesensitizationField sub = buildAst(null,str.substring(pointIndex+1),false);

        if(parent.getFieldMap()==null){
            parent.setFieldMap(new HashMap<>());
        }

        parent.getFieldMap().put(sub.getKeyName(),sub);

        return parent;
    }



    /**
     * 获取响应报文的json格式字符串
     *
     * @param annotation
     * @param method
     * @param returnObj
     * @return
     */
    public static String getRespDataJsonStr(OpRecordAnnotation annotation , Method method, Object returnObj) throws Exception {

        // 脱敏字段转换为ast
        Map<String,DesensitizationField> ast = buildAstByDtLevel(method,annotation.respDtLevel(),annotation.respDtFields());
        assert ast != null;

        // 返回值类型所有的字段的名称
        List<String> fieldNameList = Arrays.stream(method.getReturnType().getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toList());

        // 返回值所有字段的field
        Map<String,Field> returnObjFieldMap=getClassFieldMap(returnObj.getClass());

        JSONArray data= new JSONArray();
        for(int i=0;i<fieldNameList.size();i++ ){
            String fieldName = fieldNameList.get(i);
            Field returnField = returnObjFieldMap.get(fieldName);
            returnField.setAccessible(true);
            data.set(i,processDtField(annotation.respDtLevel(),ast.get(fieldName),fieldName,returnField.get(returnObj),annotation.symbol()));
        }

        return data.toJSONString();
    }

    public static Map<String,Field> getClassFieldMap(Class<?> clazz){
        if(CACHE_FIELD.containsKey(clazz)){
            return CACHE_FIELD.get(clazz);
        }

        Map<String,Field> returnObjFieldMap=Arrays.stream(clazz.getDeclaredFields())
                .collect(Collectors.toMap(Field::getName, Function.identity()));

        CACHE_FIELD.put(clazz,returnObjFieldMap);

        return returnObjFieldMap;
    }



    /**
     * 获取请求入参的json格式字符串
     *
     * @param annotation OpRecordAnnotation
     * @param method  method
     * @param args        实际入参
     * @return
     */
    public static String getReqDataJsonStr(OpRecordAnnotation annotation ,Method method , Object[] args) {

        // 脱敏字段转换为ast
        Map<String,DesensitizationField> ast = buildAstByDtLevel(method,annotation.reqDtLevel(),annotation.reqDtFields());

        // method的所有入参名称
        List<String> fieldNameList =Arrays.stream(method.getParameters())
                .map(Parameter::getName)
                .collect(Collectors.toList());

        JSONArray data= new JSONArray();
        for(int i=0;i<fieldNameList.size();i++ ){
            String fieldName = fieldNameList.get(i);
            data.set(i,processDtField(annotation.reqDtLevel(),ast.get(fieldName),fieldName,args[i],annotation.symbol()));
        }

        return data.toJSONString();
    }

    public static Object processDtField(DesensitizationLevel level,  DesensitizationField dField,  String fieldName, Object arg,String symbol){
        JSONObject result = new JSONObject();
        Object value = null;

        if(arg!=null){
            if(dField==null){
                value = JSONObject.toJSON(arg);
            }else {
                // 根据level处理
                switch (level){
                    case ALL:
                        value=symbol;
                        break;
                    case NO:
                        value = JSONObject.toJSON(arg);
                        break;
                    case PART:
                        value = processDtFieldByType(level,dField,arg,symbol);
                        break;
                }
            }
        }

       result.put(fieldName,value);

        return result;
    }

    private static Object processDtFieldByType(DesensitizationLevel level,DesensitizationField dField, Object arg, String symbol) {
        switch (dField.getType()){
            case SIMPLE:
                if(dField.getFieldMap()==null){
                    return symbol;
                }else {
                    return processDtFieldByNextLayer(level,dField,arg,symbol);
                }
            case ARRAY:
                // arg转换为数组
                JSONArray array=null;
                if(arg.getClass().isArray()){
                    array = (JSONArray)JSONArray.toJSON(arg);
                }else if (arg instanceof JSONArray){
                    array=(JSONArray) arg;
                }
                if(array!=null){
                    return processDesensitizationArrayField(dField,array,symbol);
                }

            default:
                // do nothing
        }
        return null;
    }


    private static Object processDtFieldByNextLayer(DesensitizationLevel level,DesensitizationField dField, Object arg, String symbol){
        JSONObject jsonObject = arg instanceof JSONObject ? (JSONObject)arg : (JSONObject)JSONObject.toJSON(arg);
        for(String fieldName : jsonObject.keySet()){
            jsonObject.put(fieldName,processDtField(level,dField.getFieldMap().get(fieldName),fieldName,jsonObject.get(fieldName),symbol ));
        }
        return jsonObject;
    }


    /**
     * 获取脱敏后的json字符串
     * @param level
     * @param ast
     * @param symbol
     * @param args
     * @return
     */
   private static String getDesensitizationJsonStr(DesensitizationLevel level, Map<String, DesensitizationField> ast, String symbol, List<String> nameArray, Object[] args){
       JSONArray jsonArray = new JSONArray();

       for(int i=0;i<nameArray.size();i++){
           JSONObject jsonObject = new JSONObject();

           String fieldName = nameArray.get(i);
           Object value = null;
           switch (level){
               case ALL:
                   // 全脱敏
                   value=symbol;
                   break;
               case NO:
                   // 不脱敏
                   value= JSONObject.toJSON(args[i]);
                   break;
               case PART:
                   // 部分脱敏
                   JSONObject json = (JSONObject)JSONObject.toJSON(args[i]);
                   value = astMaybe2Json(ast,fieldName,json,symbol);
                   break;
               default:
                   // do nothing
           }
           jsonObject.put(fieldName,value);
           jsonArray.add(jsonObject);
       }

       return jsonArray.toJSONString();


   }

    private static Object astMaybe2Json(Map<String, DesensitizationField> ast, String fieldName, JSONObject arg, String symbol) {
       // fieldName不是脱敏字段
       if(!ast.containsKey(fieldName)){
           return arg;
       }else {
           // 是脱敏字段
           return processDesensitizationField(ast.get(fieldName),arg,symbol);
       }
    }

    private static Object processDesensitizationField(DesensitizationField dField,JSONObject arg, String symbol) {

       if(arg==null){
           return null;
       }

       // todo:
        switch (dField.getType()){
            case SIMPLE:
                if(dField.getFieldMap()==null){
                    arg.put(dField.getKeyName(),symbol);
                    return arg;
                }else {
                    return processDesensitizationSubLayer(dField,arg,symbol);
                }
            case ARRAY:
                // arg是数组或者List
                if(arg.getClass().isArray() || arg instanceof List){
                    JSONArray array = (JSONArray)JSONArray.toJSON(arg);
                    return processDesensitizationArrayField(dField,array,symbol);
                }

            default:
                // do nothing
        }

       return null;
    }

    private static Object processDesensitizationSubLayer(DesensitizationField dField, JSONObject arg, String symbol) {

       for(Map.Entry<String,DesensitizationField> entry:dField.getFieldMap().entrySet()){
           if(arg.containsKey(entry.getKey())){
               Object newArg = arg.get(entry.getKey());
               DesensitizationField subField = dField.getFieldMap().get(entry.getKey());
               if(subField!=null){
                   arg.put(entry.getKey(), symbol);
               }else {
                   arg.put(entry.getKey(), processDesensitizationField(dField.getFieldMap().get(entry.getKey()),(JSONObject)JSONObject.toJSON(newArg),symbol ));
               }

           }
       }
       return arg;
    }

    private static Object processDesensitizationArrayField(DesensitizationField dField, JSONArray array, String symbol) {
        // 没有下一层得标识
        boolean notHadNextLevel=dField.getFieldMap()==null;
       for(int i=0;i< array.size(); i++){
          // 当前元素的小标是否符合表达式的标识
           boolean match=matchArrayExpValue(i,dField.getArrayExps());
               if(match){
                    if(notHadNextLevel){
                        array.set(i,symbol);
                    }else {
                        // 有下一层
                        for(Map.Entry<String,DesensitizationField> entrySet:dField.getFieldMap().entrySet()){
                            Object arg = array.get(i);
                            JSONObject jsonObject = null;
                            if(arg instanceof JSONObject){
                                jsonObject=(JSONObject)arg;
                            }else {
                                jsonObject=(JSONObject)JSONObject.toJSON(arg);
                            }
                            Object value= processDesensitizationField(dField.getFieldMap().get(entrySet.getKey()),jsonObject,symbol);
                            array.set(i,value);
                        }
                    }
               }
       }

       return array;
   }



    private static boolean matchArrayExpValue(int i, List<ArrayExpValue> arrayExps) {
        for (ArrayExpValue expValue : arrayExps) {
            // 星号
            if (expValue.getMode().equals(ArrayExpValue.ArrayExpMode.ASTERISK)
                    //按数组下标精确匹配
                    || (expValue.getMode().equals(ArrayExpValue.ArrayExpMode.PRECISE) && Integer.parseInt(expValue.getLeft()) == i)
                    // 范围匹配
                    || (expValue.getMode().equals(ArrayExpValue.ArrayExpMode.RANG) && i >= Integer.parseInt(expValue.getLeft()) && i >= Integer.parseInt(expValue.getRight()))
                    // 小于
                    || (expValue.getMode().equals(ArrayExpValue.ArrayExpMode.LESS) && i < Integer.parseInt(expValue.getRight()))
                    //  小于等于
                    || (expValue.getMode().equals(ArrayExpValue.ArrayExpMode.LESS_EQ) && i <= Integer.parseInt(expValue.getRight()))
                    //  大于
                    || (expValue.getMode().equals(ArrayExpValue.ArrayExpMode.GREATER) && i > Integer.parseInt(expValue.getLeft()))
                    // 大于等于
                    || (expValue.getMode().equals(ArrayExpValue.ArrayExpMode.GREATER_EQ) && i >= Integer.parseInt(expValue.getLeft()))
            ) {
                return true;
            }
        }
        return false;
    }


    private static Map<String, DesensitizationField> buildAstByDtLevel(Method method,DesensitizationLevel level, String[] exps) {
        if (Objects.requireNonNull(level) == DesensitizationLevel.PART) {
            Map<String, DesensitizationField> ast = new HashMap<>();
            for (String exp : exps) {
                // 脱敏字段转换为ast
                DesensitizationField dField = buildAst(method, exp, true);
                if (ast.containsKey(dField.getKeyName())) {
                    mergeDesensitizationField(ast.get(dField.getKeyName()), dField);
                } else {
                    ast.put(dField.getKeyName(), dField);
                }
            }
            if (!APPROVED_EXP.containsKey(method)) {
                APPROVED_EXP.put(method, ast);
            }
            return ast;
        }
        return null;

    }

    private static void mergeDesensitizationField(DesensitizationField dField1, DesensitizationField dField2) {
       for(Map.Entry<String, DesensitizationField> entry : dField2.getFieldMap().entrySet()){
           if(!dField1.getFieldMap().containsKey(entry.getKey())){
               dField1.getFieldMap().put(entry.getKey(),entry.getValue());
           }else {
               mergeDesensitizationField(dField1.getFieldMap().get(entry.getKey()),entry.getValue());
           }
       }
    }


    /**
     *  要要脱敏的字符串解析成 Map<String, DesensitizationField> ，
     *   注意: paramStr可能是多颗多层级的树.
     *
     * @param paramStr 要进行脱敏的字段名集合。
     * @return Map<String, DesensitizationField>
     */
    public static Map<String, DesensitizationField> paramStr2DesensitizationFieldMap(String[] paramStr)  {

        Map<String, DesensitizationField>  fieldTree = new HashMap<>();

        for(String str:paramStr){

            String[] split = str.split("\\.");

            int layer = 1;
            DesensitizationField parent = fieldTree.get(split[0]);

            if(parent==null){
                DesensitizationField newField = new DesensitizationField();
                newField.setKeyName(split[0]);
                fieldTree.put(newField.getKeyName(),newField);
                parent = newField;
            }

            Map<String, DesensitizationField>  fieldMap = parent.getFieldMap();
            while (layer < split.length){
                if(fieldMap==null){
                    Map<String, DesensitizationField> map = new HashMap<>();
                    parent.setFieldMap(map);
                    fieldMap = map;
                }
                DesensitizationField field  = fieldMap.get(split[layer]);
                if(field==null){
                    field  = new DesensitizationField();
                    field.setKeyName(split[layer]);
                    fieldMap.put(field.getKeyName(),field);
                }else {
                    fieldMap = field.getFieldMap();
                    parent = field;
                }
                layer++;
            }

        }

        return fieldTree;
    }



    /**
     *   脱敏
     * @param str 要进行脱敏的json字符串
     * @param fieldNameMap 指定的需要进行脱敏的字段名 (如果allFlg为true,则该值可以为null)
     * @param symbol 用来代替敏感信息的字符串
     * @param allFlg 是否对所有字段进行脱敏
     * @return 脱敏后的字符串
     */
    public static String desensitization(String str, Map<String, DesensitizationField> fieldNameMap , String symbol , boolean allFlg) throws Exception{

        if(StringUtils.isEmpty(str)){
            throw new Exception("str is null!");
        }

        if(StringUtils.isEmpty(symbol)){
            throw new Exception("symbol is null!");
        }

        if(!allFlg && CollectionUtils.isEmpty(fieldNameMap)){
            throw new Exception("desensitization failed ! fieldNameSet is null.");
        }

        StringBuilder builder = new StringBuilder("[");

        Object[] array = JSONArray.parseArray(str)
                .stream()
                .toArray();

        for(int i=0;i<array.length;i++){
            JSONObject jo = (JSONObject) array[i];
            if(jo == null){
              continue;
            }
            replaceJsonValue(jo,fieldNameMap,symbol,allFlg);
            builder.append(jo.toJSONString());
            if(i<array.length-1){
                builder.append(",");
            }
        }
        builder.append("]");
        return builder.toString();
    }


    public static void replaceJsonValue(JSONObject obj, Map<String, DesensitizationField> fieldMap , String newValue,boolean allFlg){
        obj.entrySet()
                .forEach(e->{
                    if(allFlg){
                        e.setValue(newValue);
                    }else if(fieldMap.containsKey(e.getKey())){
                        DesensitizationField field = fieldMap.get(e.getKey());
                        // field.getFieldMap()为空，说明没有下一层。
                        if(CollectionUtils.isEmpty(field.getFieldMap())){
                            e.setValue(newValue);
                        }else if(e.getValue()!=null && e.getValue() instanceof JSONObject ){
                            replaceJsonValue((JSONObject) e.getValue(),field.getFieldMap(),newValue,allFlg);
                        }
                    }
                });
    }

}
