package com.teamtrace.backend.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthHttpsEnforcementFilterTest {

    @Test
    void shouldRejectInsecureRemoteAuthRequest() throws Exception {
        AuthHttpsEnforcementFilter filter = new AuthHttpsEnforcementFilter(new ObjectMapper(), true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServerName("teamtrace.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("HTTPS_REQUIRED"));
    }

    @Test
    void shouldAllowForwardedHttpsAuthRequest() throws Exception {
        AuthHttpsEnforcementFilter filter = new AuthHttpsEnforcementFilter(new ObjectMapper(), true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServerName("teamtrace.example.com");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest());
    }

    @Test
    void shouldAllowLocalHttpAuthRequest() throws Exception {
        AuthHttpsEnforcementFilter filter = new AuthHttpsEnforcementFilter(new ObjectMapper(), true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServerName("localhost");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest());
    }
}
