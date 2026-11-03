package com.orionkey.finance;

public final class FinanceCsv {
    private FinanceCsv(){}
    public static String cell(Object input){
        String value=input==null?"":input.toString();
        String trimmed=value.stripLeading();
        if(!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0)value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
