package com.test;

import com.test.car.Display;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.io.IOException;

/**
 * @author : Mr-Z
 * @date : 2020/07/06 22:52
 */
public class MyApplication {
 	public static void main(String[] args) throws IOException {
		Resource classPathResource = new FileSystemResource("F:\\Code\\java\\study\\git\\spring-framework\\spring-test\\src\\main\\resources\\beans.xml");
//		Resource classPathResource2 = new FileSystemResource("H:/Code/study/spring-framework/spring-test/src/main/resources/beans2.xml");
//		Resource classPathResource3 = new ClassPathResource("xml/beans.xml");
		XmlBeanFactory bf =new XmlBeanFactory(classPathResource);

		Person person = bf.getBean("pee", Person.class);
		Display display = bf.getBean("display", Display.class);
		display.display();
		bf.removeBeanDefinition("person");
//		Person2 person2 = bf.getBean("person2", Person2.class);
//		System.out.println(person2.getName2());
//		System.out.println(person.getPerson2().getName2());
//		System.out.println(person);
//		User user = bf.getBean("user", User.class);
//		System.out.println(user);
//		ClassPathResource resource = new ClassPathResource("beans.xml");
//		System.out.println(resource.exists());
//		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
//		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
//		reader.loadBeanDefinitions(resource);
//		Person person = factory.getBean("pee", Person.class);
//		System.out.println(person);

//		PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
//		pathMatchingResourcePatternResolver.getResources("classpath*:test*/cc*/dd/ff/beans.xml");


//		ResourceLoader
//		person.getClass().getClassLoader();
//		System.out.println(person.getName());
//		ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:beans.xml");
//		ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
//		Person person = context.getBean("pee", Person.class);
//		System.out.println(person.getName());

//		ResourceLoader resourceLoader = new DefaultResourceLoader();
//
//		Resource fileResource1 = resourceLoader.getResource("http://static.iocoder.cn/2446cc9fba90605b691ea250cf340ebb");
//		System.out.println("fileResource1 is FileSystemResource:" + (fileResource1 instanceof FileSystemResource));



//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../core/io/./Resource.class"));
//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../asm/./Edge.class"));
//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../../../asm/./Edge.class"));
//		System.out.println(StringUtils.cleanPath("file:\\dir\\test.txt?argh"));

//		AlternativeJdkIdGenerator alternativeJdkIdGenerator = new AlternativeJdkIdGenerator();
//		System.out.println(alternativeJdkIdGenerator.generateId());
//		test();
	}

	public static void test() {
		PathMatcher pathMatcher = new AntPathMatcher();

		// 精确匹配
//		System.out.println(pathMatcher.match("/test", "/test"));
//		System.out.println(pathMatcher.match("test", "/test"));

		//测试通配符?
//		System.out.println(pathMatcher.match("t?st", "test"));
//		System.out.println(pathMatcher.match("te??", "test"));
//		System.out.println(pathMatcher.match("tes?", "tes"));
//		System.out.println(pathMatcher.match("tes?", "testt"));

		//测试通配符*
//		System.out.println(pathMatcher.match("*", "test"));
//		System.out.println(pathMatcher.match("test*", "test"));
//		System.out.println(pathMatcher.match("test/*", "test/Test"));
//		System.out.println(pathMatcher.match("*.*", "test."));
//		System.out.println(pathMatcher.match("*.*", "test.test.test"));
//		System.out.println(pathMatcher.match("test*", "test/")); //注意这里是false 因为路径不能用*匹配
//		System.out.println(pathMatcher.match("test*", "test/t")); //这同理
//		System.out.println(pathMatcher.match("test*aaa", "testblaaab")); //这个是false 因为最后一个b无法匹配了 前面都是能匹配成功的

		//测试通配符** 匹配多级URL
//		System.out.println(pathMatcher.match("/*/**", "/testing/testing"));
//		System.out.println(pathMatcher.match("/**/*", "/testing/testing"));
//		System.out.println(pathMatcher.match("/bla/**/bla", "/bla/testing/testing/bla/bla")); //这里也是true哦
//		System.out.println(pathMatcher.match("/bla*bla/test", "/blaXXXbl/test"));

//		System.out.println(pathMatcher.match("/????", "/bala/bla"));
//		System.out.println(pathMatcher.match("/**/*bla", "/bla/bla/bla/bbb"));

//		System.out.println(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing/"));
//		System.out.println(pathMatcher.match("/*bla*/**/bla/*", "/XXXblaXXXX/testing/testing/bla/testing"));
//		System.out.println(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing"));
//		System.out.println(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing.jpg"));
//		System.out.println(pathMatcher.match("/foo/bar/**", "/foo/bar"));

		//这个需要特别注意：{}里面的相当于Spring MVC里接受一个参数一样，所以任何东西都会匹配的
//		System.out.println(pathMatcher.match("/{bla}.html", "/testing.html.html"));
//		System.out.println(pathMatcher.match("/{bla}.htm", "/testing.html")); //这样就是false了
	}

}
