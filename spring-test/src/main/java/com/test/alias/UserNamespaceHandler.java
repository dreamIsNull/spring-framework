package com.test.alias;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * @author : Mr-Z
 * @date : 2020/08/19 21:48
 */
public class UserNamespaceHandler extends NamespaceHandlerSupport {
	@Override
	public void init() {
		System.out.println("*******************[UserNamespaceHandler]********************");
		registerBeanDefinitionParser("user", new UserDefinitionParser());
	}
}
