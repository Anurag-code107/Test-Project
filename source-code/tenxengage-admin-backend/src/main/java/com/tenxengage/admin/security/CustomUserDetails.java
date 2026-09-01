package com.tenxengage.admin.security;

import com.tenxengage.admin.entity.User;
import com.tenxengage.admin.entity.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final UserStatus status;
    private final UUID clientId;
    private final UUID partnerCompanyId;
    private final Set<SimpleGrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.status = user.getStatus();
        this.clientId = user.getClientId();
        this.partnerCompanyId = user.getPartnerCompanyId();
        // Admin backend: all authenticated users get ROLE_ADMIN authority.
        // TenX admin detection uses clientId == null (see isTenxAdmin()).
        this.authorities = Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getPartnerCompanyId() {
        return partnerCompanyId;
    }

    public boolean isTenxAdmin() {
        return clientId == null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
