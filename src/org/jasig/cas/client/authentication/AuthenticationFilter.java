/*
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.cas.client.authentication;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.ReflectUtils;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.webService.CasWebService;

import utry.bo.LoginBean;
import utry.common.LoginInfoParams;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.caucho.hessian.client.HessianProxyFactory;

/**
 * Filter implementation to intercept all requests and attempt to authenticate
 * the user by redirecting them to CAS (unless the user has a ticket).
 * <p>
 * This filter allows you to specify the following parameters (at either the context-level or the filter-level):
 * <ul>
 * <li><code>casServerLoginUrl</code> - the url to log into CAS, i.e. https://cas.rutgers.edu/login</li>
 * <li><code>renew</code> - true/false on whether to use renew or not.</li>
 * <li><code>gateway</code> - true/false on whether to use gateway or not.</li>
 * </ul>
 *
 * <p>Please see AbstractCasFilter for additional properties.</p>
 *
 * @author Scott Battaglia
 * @author Misagh Moayyed
 * @since 3.0
 */
public class AuthenticationFilter extends AbstractCasFilter {
	
	private final static String REPLACESTR = "hessionService";
	
	private final static String CASTGC = "CASTGC";
	
    /**
     * The URL to the CAS Server login.
     */
    private String casServerLoginUrl;
    
    /**
     * The URL to the CAS webService;
     */
    private String webService;

    /**
     * Whether to send the renew request or not.
     */
    private boolean renew = false;

    /**
     * Whether to send the gateway request or not.
     */
    private boolean gateway = false;

    private GatewayResolver gatewayStorage = new DefaultGatewayResolverImpl();

    private AuthenticationRedirectStrategy authenticationRedirectStrategy = new DefaultAuthenticationRedirectStrategy();
    
    private UrlPatternMatcherStrategy ignoreUrlPatternMatcherStrategyClass = null;
    
    private UrlPatternMatcherStrategy ignore302CdoeUrlClass = null;
    
    private static final Map<String, Class<? extends UrlPatternMatcherStrategy>> PATTERN_MATCHER_TYPES =
            new HashMap<String, Class<? extends UrlPatternMatcherStrategy>>();
    
    static {
        PATTERN_MATCHER_TYPES.put("CONTAINS", ContainsPatternUrlPatternMatcherStrategy.class);
        PATTERN_MATCHER_TYPES.put("REGEX", RegexUrlPatternMatcherStrategy.class);
        PATTERN_MATCHER_TYPES.put("EXACT", ExactUrlPatternMatcherStrategy.class);
    }
    
    protected void initInternal(final FilterConfig filterConfig) throws ServletException {
        if (!isIgnoreInitConfiguration()) {
            super.initInternal(filterConfig);
            setCasServerLoginUrl(getPropertyFromInitParams(filterConfig, "casServerLoginUrl", null));
            logger.trace("Loaded CasServerLoginUrl parameter: {}", this.casServerLoginUrl);
            setRenew(parseBoolean(getPropertyFromInitParams(filterConfig, "renew", "false")));
            logger.trace("Loaded renew parameter: {}", this.renew);
            setGateway(parseBoolean(getPropertyFromInitParams(filterConfig, "gateway", "false")));
            logger.trace("Loaded gateway parameter: {}", this.gateway);
            setWebService(getPropertyFromInitParams(filterConfig, "webService", null));
            logger.trace("Loaded webService parameter: {}", this.webService);
            if(StringUtils.isBlank(this.webService)){
	            if(StringUtils.isNotBlank(casServerLoginUrl)){
	            	webService = this.casServerLoginUrl.replaceAll("login", REPLACESTR);
	            	this.setWebService(webService);
	            }
            }else{
            	// 因部分网络配置特殊，添加一个自配置hession地址
            	webService += "/" + REPLACESTR;
            	this.setWebService(webService);
            }
            
            final String ignorePattern_302Code = getPropertyFromInitParams(filterConfig, "ignorePattern_302Code", null);
            logger.trace("Loaded ignorePattern_302Code parameter: {}", ignorePattern_302Code);
                       
            final String ignorePattern = getPropertyFromInitParams(filterConfig, "ignorePattern", null);
            logger.trace("Loaded ignorePattern parameter: {}", ignorePattern);
            
            final String ignoreUrlPatternType = getPropertyFromInitParams(filterConfig, "ignoreUrlPatternType", "REGEX");
            logger.trace("Loaded ignoreUrlPatternType parameter: {}", ignoreUrlPatternType);
            
            if (ignorePattern != null) {
                final Class<? extends UrlPatternMatcherStrategy> ignoreUrlMatcherClass = PATTERN_MATCHER_TYPES.get(ignoreUrlPatternType);
                if (ignoreUrlMatcherClass != null) {
                    this.ignoreUrlPatternMatcherStrategyClass = ReflectUtils.newInstance(ignoreUrlMatcherClass.getName());
                } else {
                    try {
                        logger.trace("Assuming {} is a qualified class name...", ignoreUrlPatternType);
                        this.ignoreUrlPatternMatcherStrategyClass = ReflectUtils.newInstance(ignoreUrlPatternType);
                    } catch (final IllegalArgumentException e) {
                        logger.error("Could not instantiate class [{}]", ignoreUrlPatternType, e);
                    }
                }
                if (this.ignoreUrlPatternMatcherStrategyClass != null) {
                    this.ignoreUrlPatternMatcherStrategyClass.setPattern(ignorePattern);
                }
            }
            
            if (ignorePattern_302Code != null) {
                final Class<? extends UrlPatternMatcherStrategy> ignoreUrlMatcherClass = PATTERN_MATCHER_TYPES.get(ignoreUrlPatternType);
                if (ignoreUrlMatcherClass != null) {
                    this.ignore302CdoeUrlClass = ReflectUtils.newInstance(ignoreUrlMatcherClass.getName());
                } else {
                    try {
                        logger.trace("Assuming {} is a qualified class name...", ignoreUrlPatternType);
                        this.ignore302CdoeUrlClass = ReflectUtils.newInstance(ignoreUrlPatternType);
                    } catch (final IllegalArgumentException e) {
                        logger.error("Could not instantiate class [{}]", ignoreUrlPatternType, e);
                    }
                }
                if (this.ignore302CdoeUrlClass != null) {
                    this.ignore302CdoeUrlClass.setPattern(ignorePattern_302Code);
                }
            }
            
            final String gatewayStorageClass = getPropertyFromInitParams(filterConfig, "gatewayStorageClass", null);

            if (gatewayStorageClass != null) {
                this.gatewayStorage = ReflectUtils.newInstance(gatewayStorageClass);
            }
            
            final String authenticationRedirectStrategyClass = getPropertyFromInitParams(filterConfig,
                    "authenticationRedirectStrategyClass", null);

            if (authenticationRedirectStrategyClass != null) {
                this.authenticationRedirectStrategy = ReflectUtils.newInstance(authenticationRedirectStrategyClass);
            }
        }
    }

    public void init() {
        super.init();
        CommonUtils.assertNotNull(this.casServerLoginUrl, "casServerLoginUrl cannot be null.");
    }

    public final void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {
        
        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        final HttpServletResponse response = (HttpServletResponse) servletResponse;
        final String requestType = request.getHeader("X-Requested-With");
        final String url = request.getRequestURI();
        
        if (isRequestUrlExcluded(request)) {
            logger.debug("Request is ignored.");
            filterChain.doFilter(request, response);
            return;
        }
        
        final HttpSession session = request.getSession(false);
        final Assertion assertion = session != null ? (Assertion) session.getAttribute(CONST_CAS_ASSERTION) : null;

        // 同步调整，避免出现使用浏览器缓存的cookie值，过滤comet和hessionService这两个使用ticket
        final String mainframe = request.getParameter("casRedict");
        if(StringUtils.isBlank(requestType) && StringUtils.isNotBlank(mainframe) && url.indexOf("comet") == -1 
        		&& url.indexOf("hessionService") == -1 && !is302CodeRequestUrlExcluded(request)){
        	if(null != assertion){
        		setLoginBean(request,assertion);
        		session.setAttribute(CONST_CAS_ASSERTION, null);
        		filterChain.doFilter(request, response);
        		return;
        	}
        }else{
	        SyncTicket(request);
	        LoginBean lb = LoginInfoParams.getSp();
	        if (lb != null) {
	        	filterChain.doFilter(request, response);
	        	return;
	        }
        }
        
        final String serviceUrl = constructServiceUrl(request, response);
        final String ticket = retrieveTicketFromRequest(request);
        final boolean wasGatewayed = this.gateway && this.gatewayStorage.hasGatewayedAlready(request, serviceUrl);

        if (CommonUtils.isNotBlank(ticket) || wasGatewayed) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 任务调度中hession 根据token和hession 过滤
        final String hessionToken = request.getHeader("token");
        if(StringUtils.isNotBlank(hessionToken) && url.indexOf("hessionService") > -1){
	        filterChain.doFilter(request, response);
	        return;
        }

        final String modifiedServiceUrl;

        logger.debug("no ticket and no assertion found");
        if (this.gateway) {
            logger.debug("setting gateway attribute in session");
            modifiedServiceUrl = this.gatewayStorage.storeGatewayInformation(request, serviceUrl);
        } else {
            modifiedServiceUrl = serviceUrl;
        }

        logger.debug("Constructed service url: {}", modifiedServiceUrl);

        final String urlToRedirectTo = CommonUtils.constructRedirectUrl(this.casServerLoginUrl,
                getServiceParameterName(), modifiedServiceUrl, this.renew, this.gateway);

        logger.debug("redirecting to \"{}\"", urlToRedirectTo);
        
        if (StringUtils.isNotBlank(requestType)) {
        	String referer = request.getHeader("referer");
        	int i = 0;
        	if ((i = referer.indexOf("jid=")) > -1) {
        		referer = referer.substring(0, i + 4) + Math.random();
        	}
        	referer = CommonUtils.constructRedirectUrl(this.casServerLoginUrl, 
            getServiceParameterName(), referer, this.renew, this.gateway);
        	JSONObject json = new JSONObject();
        	json.put("login", Boolean.valueOf(true));
        	response.getWriter().print(json);
        }else {
          this.authenticationRedirectStrategy.redirect(request, response, urlToRedirectTo);
        }
    }

    public final void setRenew(final boolean renew) {
        this.renew = renew;
    }

    public final void setGateway(final boolean gateway) {
        this.gateway = gateway;
    }

    public final void setCasServerLoginUrl(final String casServerLoginUrl) {
        this.casServerLoginUrl = casServerLoginUrl;
    }

    public final void setGatewayStorage(final GatewayResolver gatewayStorage) {
        this.gatewayStorage = gatewayStorage;
    }
        
    private boolean isRequestUrlExcluded(final HttpServletRequest request) {
        if (this.ignoreUrlPatternMatcherStrategyClass == null) {
            return false;
        }
        
        final StringBuffer urlBuffer = request.getRequestURL();
        if (request.getQueryString() != null) {
            urlBuffer.append("?").append(request.getQueryString());
        }
        final String requestUri = urlBuffer.toString();
        return this.ignoreUrlPatternMatcherStrategyClass.matches(requestUri);
    }
    
    private boolean is302CodeRequestUrlExcluded(final HttpServletRequest request) {
        if (this.ignore302CdoeUrlClass == null) {
            return false;
        }
        
        final StringBuffer urlBuffer = request.getRequestURL();
        if (request.getQueryString() != null) {
            urlBuffer.append("?").append(request.getQueryString());
        }
        final String requestUri = urlBuffer.toString();
        return this.ignore302CdoeUrlClass.matches(requestUri);
    }
    
    @SuppressWarnings("unused")
	private void Dispatcher(HttpServletRequest request, HttpServletResponse response) throws Exception{
    	String loginUrl = "sys/userLogin/franmeIndex.do";
		String requestType = request.getHeader("X-Requested-With");
		String outHtml = "";
		if(StringUtils.isBlank(requestType)){
			ServletOutputStream out = response.getOutputStream();
			outHtml = "<script language=\"javascript\">"
					+ "if(parent.parent.parent.parent!=null)parent.parent.parent.parent.location.href='"
					+ request.getContextPath() + loginUrl + "';"
					+ "if(parent.parent.parent!=null)parent.parent.parent.location.href='"
					+ request.getContextPath() + loginUrl + "';"
						+ "if(parent.parent!=null)parent.parent.location.href='"
						+ request.getContextPath() + loginUrl + "';"
						+ "else if(parent!=null)parent.location.href='"
						+ request.getContextPath() + loginUrl + "';"
						+ " else window.location.href='" + request.getContextPath()
						+ loginUrl + "';" + "</script>";
			out.print(outHtml);
			out.flush();
			out.close();
		}else{
			outHtml = "{\"login\":true}";
			response.setContentType("application/json; charset=utf-8");
			response.getWriter().write(outHtml);
		}
		
	}

	public String getWebService() {
		return webService;
	}

	public void setWebService(String webService) {
		this.webService = webService;
	}
	
	/**
	 * 创建通信服务
	 * @param url 服务地址
	 * @return 通信服务
	 */
	private CasWebService createWebService(String url){
		HessianProxyFactory factory = new HessianProxyFactory();
		CasWebService service = null;
		try {
			service = (CasWebService)factory.create(CasWebService.class,url);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return service;
	}
	
	/**
	 * 获取cookie值
	 * @param request 请求对象
	 * @param name 名字
	 * @return
	 */
	private String getWebCookie(HttpServletRequest request,String name){
		String tgtId = "";
		Cookie[] cookies = request.getCookies();
		if(null != cookies){
			for(Cookie cookie:cookies){
				if(CASTGC.equals(cookie.getName())){
					tgtId = cookie.getValue();
					break;
				}
			}
		}
		return tgtId;
	}
	
	/**
	 * 同步票据，并保存用户信息
	 * @param request
	 */
	private void SyncTicket(HttpServletRequest request){
		String tgtId = "";
		// 获取tgtId
		tgtId = request.getHeader("ticket");
		if(StringUtils.isBlank(tgtId)){
			tgtId = getWebCookie(request,CASTGC);
		}
		// 调用cashession服务
    	CasWebService casWebService = this.createWebService(webService);
    	String s = casWebService.UpdateAndfindTGTInfo(tgtId);
    	LoginBean lb = null;
    	if(StringUtils.isNotBlank(s)){
    		JSONObject obj = JSON.parseObject(s);
    		if(null != obj){
    			lb = new LoginBean();
	    		lb.setAccountID(String.valueOf(obj.get("accountID")).trim());
		    	lb.setCompanyID(String.valueOf(obj.get("companyID")).trim());
		    	lb.setLoginName(String.valueOf(obj.get("account")).trim());
		    	lb.setRealName(String.valueOf(obj.get("realName")).trim());
		    	Map<String,Object> map = new HashMap<String,Object>();
		    	map.put("ticket", tgtId);
		    	lb.setMap(map);
    		}
    	}
    	LoginInfoParams.putSp(lb);
	}
	
	/**
	 * 从session中获取
	 * @param request
	 */
	public void setLoginBean(HttpServletRequest request,Assertion assertion){
		String tgtId = "";
		// 获取tgtId
		tgtId = request.getHeader("ticket");
		if(StringUtils.isBlank(tgtId)){
			tgtId = getWebCookie(request,CASTGC);
		}
		LoginBean lb = new LoginBean();
		AttributePrincipal principal = (AttributePrincipal) assertion.getPrincipal();
		Map<String,Object> attributes=null;
		if(principal!=null)
	    attributes = principal.getAttributes();
	    if (attributes != null)  {
	    	lb.setAccountID(String.valueOf(attributes.get("accountID")).trim());
	    	lb.setCompanyID(String.valueOf(attributes.get("companyID")).trim());
	    	lb.setLoginName(String.valueOf(attributes.get("account")).trim());
	    	lb.setRealName(String.valueOf(attributes.get("realName")).trim());
	    	Map<String,Object> map = new HashMap<String,Object>();
	    	map.put("ticket", tgtId);
	    	lb.setMap(map);
        }else{ 
        	logger.debug("无法获取到LoginBean");
        }
	    LoginInfoParams.putSp(lb);
	}
}
