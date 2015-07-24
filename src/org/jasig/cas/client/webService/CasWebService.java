/*类名：webService接口*/
/*
* 创建人：chenjie 
* 创建时间：2014-11-6 
* 备注：新建
*/
package org.jasig.cas.client.webService;

import javax.jws.WebService;

/**
 * webService接口
 */
@WebService 
public interface CasWebService {
	
	/**
	 * 创建TGT
	 * @param userName 用户名
	 * @param password 密码
	 * @return 票据
	 */
	public String createTicketGrantingTicket(String userName,String password);
	
	/**
	 * 销毁票据
	 * @param ticketGrantingTicketId 票据id
	 */
	public void destroyTicketGrantingTicket(final String ticketGrantingTicketId);
	
	/**
	 * 获取用户信息
	 * @param ticketGrantingTicketId 票据id
	 * @return
	 */
	public String UpdateAndfindTGTInfo(final String ticketGrantingTicketId);
} 
