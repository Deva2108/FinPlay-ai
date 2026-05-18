package com.example.stockPortfolio.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LoginRateLimitFilterTest {

    private MockHttpServletRequest authReq(String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI("/api/auth/login");
        r.setRemoteAddr(ip);
        return r;
    }

    @Test
    @DisplayName("11th login attempt within 1 minute from same IP is rejected with 429")
    void enforcesPerIpLimit() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        FilterChain chain = mock(FilterChain.class);

        HttpServletResponse last = null;
        for (int i = 0; i < 11; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(authReq("9.9.9.9"), res, chain);
            last = res;
        }

        assertThat(last).isNotNull();
        assertThat(last.getStatus()).isEqualTo(429);
        // First 10 passed through; 11th was blocked before the chain.
        verify(chain, times(10)).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    @DisplayName("Different IPs have independent buckets")
    void independentIps() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 9; i++) {
            filter.doFilterInternal(authReq("1.1.1.1"), new MockHttpServletResponse(), chain);
            filter.doFilterInternal(authReq("2.2.2.2"), new MockHttpServletResponse(), chain);
        }
        verify(chain, atLeast(18)).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    @DisplayName("Non-auth paths are skipped entirely (filter is a no-op)")
    void skipsNonAuthPaths() {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI("/api/portfolios");
        assertThat(filter.shouldNotFilter(r)).isTrue();
    }
}
