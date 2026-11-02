package com.example.remoteportal.web;

import com.example.remoteportal.service.ChatGptWebService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

@RestController
public class ChatGptWebResource {
  private final ChatGptWebService chatgpt;

  public ChatGptWebResource(ChatGptWebService chatgpt){this.chatgpt=chatgpt;}

  @GetMapping("/api/chatgpt/status")
  public ChatGptWebService.WebStatus status(Principal principal){return chatgpt.status(principal.getName());}

  @GetMapping("/api/chatgpt/connect")
  public RedirectView connect(Principal principal,HttpSession session){
    try{return new RedirectView(chatgpt.begin(principal.getName(),session.getId()).toString());}
    catch(Exception ex){return new RedirectView("/chatgpt.html?error="+encode(message(ex)));}
  }

  @GetMapping("/api/chatgpt/callback")
  public RedirectView callback(
      Principal principal,
      HttpSession session,
      @RequestParam(required=false) String state,
      @RequestParam(required=false) String code,
      @RequestParam(required=false) String error){
    try{
      chatgpt.complete(principal.getName(),session.getId(),state,code,error);
      return new RedirectView("/chatgpt.html?connected=1");
    }catch(Exception ex){
      return new RedirectView("/chatgpt.html?error="+encode(message(ex)));
    }
  }

  @GetMapping("/api/chatgpt/models")
  public Map<String,Object> models(Principal principal){
    try{return Map.of("models",chatgpt.models(principal.getName()));}
    catch(Exception ex){throw apiError(ex);}
  }

  public record RespondRequest(String model,List<ChatGptWebService.ChatLine> messages){}

  @PostMapping("/api/chatgpt/respond")
  public Map<String,Object> respond(Principal principal,@RequestBody RespondRequest request){
    try{return Map.of("text",chatgpt.respond(principal.getName(),request.model(),request.messages()));}
    catch(Exception ex){throw apiError(ex);}
  }

  @PostMapping("/api/chatgpt/disconnect")
  public Map<String,Object> disconnect(Principal principal){chatgpt.disconnect(principal.getName());return Map.of("disconnected",true);}

  private static ResponseStatusException apiError(Exception ex){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message(ex));}
  private static String message(Throwable err){Throwable c=err;while(c.getCause()!=null)c=c.getCause();return c.getMessage()==null?c.toString():c.getMessage();}
  private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
