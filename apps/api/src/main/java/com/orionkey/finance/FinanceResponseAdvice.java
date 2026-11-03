package com.orionkey.finance;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Decimal strings prevent browser Number coercion from losing cents in large financial totals. */
@RestControllerAdvice(basePackages="com.orionkey.finance") @RequiredArgsConstructor
public class FinanceResponseAdvice implements ResponseBodyAdvice<Object> {
    private final FinanceSupport support;
    private final ObjectMapper mapper;
    @Override public boolean supports(MethodParameter type,Class<? extends HttpMessageConverter<?>> converter){return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);}
    @Override public Object beforeBodyWrite(Object body,MethodParameter type,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
        response.getHeaders().setCacheControl("private, no-store");
        try{return mapper.readTree(support.json(body));}catch(Exception e){throw new IllegalStateException("财务响应无法序列化",e);}
    }
}
