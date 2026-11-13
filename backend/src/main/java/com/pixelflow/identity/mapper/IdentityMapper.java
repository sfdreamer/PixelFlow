package com.pixelflow.identity.mapper;

import com.pixelflow.identity.AppUser;
import com.pixelflow.identity.SessionIdentity;
import com.pixelflow.identity.UserSession;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdentityMapper {

    void insertUser(AppUser user);

    void insertSession(UserSession session);

    SessionIdentity findIdentityByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
