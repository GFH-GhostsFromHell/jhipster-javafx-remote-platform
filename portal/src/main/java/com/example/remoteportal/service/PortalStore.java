package com.example.remoteportal.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class PortalStore {
  public static final List<String> FEATURES=List.of(
    "ELECTRICAL_CALCULATORS","RESIDENTIAL_REFERENCE",
    "DASHBOARD","REPORTS","PLC_CONNECT","REMOTE_DIAGNOSTICS","ADVANCED_ANALYTICS","ENGINEERING_MODE","ADMIN_TOOLS"
  );
  private final JdbcTemplate db;
  private final ClientAccountStore accounts;
  public PortalStore(JdbcTemplate db, ClientAccountStore accounts){this.db=db;this.accounts=accounts;}

  @PostConstruct void init(){
    db.execute("create table if not exists user_feature_override (username varchar(255) not null, feature_key varchar(255) not null, state varchar(20) not null, updated_at timestamp, updated_by varchar(255), primary key(username,feature_key))");
    db.execute("create table if not exists client_configuration (username varchar(255) not null, config_key varchar(255) not null, config_value varchar(4000), primary key(username,config_key))");
  }

  public Map<String,String> overrides(String user){
    Map<String,String> m=new LinkedHashMap<>();
    db.query("select feature_key,state from user_feature_override where username=? order by feature_key",rs->{m.put(rs.getString(1),rs.getString(2));},user);
    return m;
  }

  public Map<String,Boolean> effective(String user){
    String tier;
    try { tier = accounts.tier(user); } catch(Exception ex) { tier = "TIER_1"; }
    Map<String,Boolean> m=new LinkedHashMap<>();
    for(String f:FEATURES)m.put(f,false);
    m.put("ELECTRICAL_CALCULATORS",true);
    m.put("RESIDENTIAL_REFERENCE",true);
    m.put("DASHBOARD",true);
    m.put("REPORTS",true);
    if (List.of("TIER_2","TIER_3").contains(tier)) {
      m.put("PLC_CONNECT",true);
      m.put("REMOTE_DIAGNOSTICS",true);
    }
    if ("TIER_3".equals(tier)) {
      m.put("ADVANCED_ANALYTICS",true);
      m.put("ENGINEERING_MODE",true);
    }
    overrides(user).forEach((k,v)->{
      if(v.equals("ENABLED"))m.put(k,true);
      else if(v.equals("DISABLED"))m.put(k,false);
    });
    return m;
  }

  public void setFeature(String user,String key,String state,String actor){
    if(!FEATURES.contains(key))throw new IllegalArgumentException("Unknown feature");
    if(!List.of("INHERIT","ENABLED","DISABLED").contains(state))throw new IllegalArgumentException("Invalid state");
    int n=db.update("update user_feature_override set state=?,updated_at=?,updated_by=? where username=? and feature_key=?",state,java.sql.Timestamp.from(Instant.now()),actor,user,key);
    if(n==0)db.update("insert into user_feature_override(username,feature_key,state,updated_at,updated_by) values(?,?,?,?,?)",user,key,state,java.sql.Timestamp.from(Instant.now()),actor);
  }

  public Map<String,String> config(String user){
    Map<String,String> m=new LinkedHashMap<>();
    db.query("select config_key,config_value from client_configuration where username=? order by config_key",rs->{m.put(rs.getString(1),rs.getString(2));},user);
    return m;
  }

  public void setConfig(String user,String key,String value){
    int n=db.update("update client_configuration set config_value=? where username=? and config_key=?",value,user,key);
    if(n==0)db.update("insert into client_configuration(username,config_key,config_value) values(?,?,?)",user,key,value);
  }
}
