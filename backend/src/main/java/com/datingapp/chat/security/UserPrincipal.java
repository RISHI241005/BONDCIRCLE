package com.datingapp.chat.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Custom authenticated user principal stored inside Spring Security's SecurityContext.
 */
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    public UserPrincipal(Long userId, String username, Collection<? extends GrantedAuthority> authorities, boolean active) {
        this.userId = userId;
        this.username = username;
        this.authorities = authorities != null ? authorities : Collections.emptyList();
        this.active = active;
    }

    public static UserPrincipal create(Long userId, List<String> roles) {
        List<GrantedAuthority> authorities = (roles != null)
                ? roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new UserPrincipal(userId, String.valueOf(userId), authorities, true);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // Stateless JWT - no password retained
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPrincipal that = (UserPrincipal) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
