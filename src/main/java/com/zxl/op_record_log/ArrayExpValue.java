package com.zxl.op_record_log;

import lombok.Data;

@Data
public class ArrayExpValue {
    ArrayExpMode mode;
    String left;
    String right;

    public ArrayExpValue(ArrayExpMode mode, String left, String right) {
        this.mode = mode;
        this.left = left;
        this.right = right;
    }

    public static ArrayExpValue initByExpStr(String exp){
        // 星号
        if(exp.equals("*")){
            return new ArrayExpValue(ArrayExpMode.ASTERISK,null,null);
        }

        // 范围匹配
        String splitSymbol = "-";
        if(exp.contains(splitSymbol)){
            int idx=exp.indexOf(splitSymbol);
            String[] values=exp.split(splitSymbol);
            return new ArrayExpValue(ArrayExpMode.RANG,values[0],values[1]);
        }

        // 小于匹配.
        String lessSymbol="<";
        if(exp.startsWith(lessSymbol)){
            String[] values=exp.split(splitSymbol);
            return new ArrayExpValue(ArrayExpMode.LESS,null,values[0]);
        }

        // 小于等于
        String lessEqSymbol="<=";
        if(exp.startsWith(lessEqSymbol)){
            String[] values=exp.split(lessEqSymbol);
            return new ArrayExpValue(ArrayExpMode.LESS_EQ,null,values[0]);
        }

        // 大于匹配.
        String greaterSymbol=">";
        if(exp.startsWith(greaterSymbol)){
            String[] values=exp.split(greaterSymbol);
            return new ArrayExpValue(ArrayExpMode.GREATER,values[0],values[0]);
        }

        // 大于等于
        String greaterEqSymbol=">=";
        if(exp.startsWith(greaterEqSymbol)){
            String[] values=exp.split(greaterEqSymbol);
            return new ArrayExpValue(ArrayExpMode.GREATER_EQ,values[0],null);
        }


        // 精确匹配
        return new ArrayExpValue(ArrayExpMode.PRECISE,exp,null);
    }

    public enum ArrayExpMode {
        // 精确匹配
        PRECISE,

        // 星号=全匹配
        ASTERISK,

        // 范围匹配
        RANG,

        // 小于
        LESS,

        // 小于等于
        LESS_EQ,

        // 大于
        GREATER,

        // 大于等于
        GREATER_EQ;
    }


}
