package com.tenxengage.app.security;

import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
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

        // Include a role-based authority derived from the user's ClientRole so that
        // downstream `isAdmin` / `isPartnerAdmin` checks (e.g. ClaimService.getClaimSummary)
        // can distinguish a Client Admin from a Partner Seller. Relies on the caller
        // holding an open transaction — CustomUserDetailsService provides one.
        Set<SimpleGrantedAuthority> granted = new HashSet<>();
        granted.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (user.getClientRole() != null && user.getClientRole().getBaseRoleName() != null) {
            granted.add(new SimpleGrantedAuthority(
                    "ROLE_" + user.getClientRole().getBaseRoleName()));
        }
        this.authorities = Set.copyOf(granted);
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

    /**
     * TENX_ADMIN users are platform-level: they have no clientId and no clientRoleId.
     */
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
