package com.example.remoteportal.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice(assignableTypes = CalculatorWorkspacePageController.class)
public class CalculatorWorkspaceHelpAdvice implements ResponseBodyAdvice<String> {

  @Override
  public boolean supports(MethodParameter returnType, Class converterType) {
    return String.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public String beforeBodyWrite(
      String body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body == null || !request.getURI().getPath().endsWith("/calculators.html")) return body;
    if (body.contains("/calculator-help.js")) return body;
    return body.replace("</body>", "<script src=\"/calculator-help.js\"></script></body>");
  }
}
