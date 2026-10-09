package org.example.dormrepairsystem.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT令牌工具类
 *
 * 由Spring管理，签名密钥来自配置（jwt.secret），不再每次启动随机生成，
 * 因此重启服务后已签发的令牌依然有效，也支持多实例部署。
 */
@Component
@Slf4j
public class JwtUtil {

    /** 自定义声明：用户ID */
    public static final String CLAIM_USER_ID = "userId";
    /** 自定义声明：角色ID */
    public static final String CLAIM_ROLE_ID = "roleId";
    /** 自定义声明：用户姓名 */
    public static final String CLAIM_USER_NAME = "userName";
    /** 自定义声明：令牌类型，用于区分访问令牌和刷新令牌 */
    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** HS256要求密钥至少256位，即32字节 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-token-expiration:7200000}") long accessTokenExpiration,
                   @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足：HS256 至少需要 32 字节（256位）密钥，请修改 application.yml 或设置 JWT_SECRET 环境变量");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * 组装令牌声明
     */
    public Map<String, Object> buildClaims(Long userId, Integer roleId, String userName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ROLE_ID, roleId);
        claims.put(CLAIM_USER_NAME, userName);
        return claims;
    }

    /**
     * 生成访问令牌
     */
    public String generateAccessToken(Map<String, Object> claims) {
        return generateToken(claims, TOKEN_TYPE_ACCESS, accessTokenExpiration);
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(Map<String, Object> claims) {
        return generateToken(claims, TOKEN_TYPE_REFRESH, refreshTokenExpiration);
    }

    private String generateToken(Map<String, Object> claims, String tokenType, long expiration) {
        Map<String, Object> payload = new HashMap<>(claims);
        // 打上令牌类型，避免刷新令牌被当成访问令牌使用
        payload.put(CLAIM_TOKEN_TYPE, tokenType);

        Date now = new Date();
        return Jwts.builder()
                .setClaims(payload)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌
     *
     * @throws io.jsonwebtoken.JwtException 令牌无效或已过期
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 使用刷新令牌换取新的访问令牌
     *
     * @throws IllegalArgumentException 传入的不是刷新令牌
     * @throws io.jsonwebtoken.JwtException 令牌无效或已过期
     */
    public String refreshAccessToken(String refreshToken) {
        Claims claims = parseToken(refreshToken);
        if (!TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE))) {
            throw new IllegalArgumentException("该令牌不是刷新令牌，无法用于刷新访问令牌");
        }

        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put(CLAIM_USER_ID, claims.get(CLAIM_USER_ID));
        newClaims.put(CLAIM_ROLE_ID, claims.get(CLAIM_ROLE_ID));
        newClaims.put(CLAIM_USER_NAME, claims.get(CLAIM_USER_NAME));
        return generateAccessToken(newClaims);
    }

    /**
     * 判断是否为访问令牌
     */
    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE));
    }
}
