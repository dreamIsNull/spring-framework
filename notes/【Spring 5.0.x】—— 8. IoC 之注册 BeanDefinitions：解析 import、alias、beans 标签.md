> 参考网址：<http://cmsblogs.com/?p=2724>

#### 目录

* [1. import 标签](#1)
  * [1.1 import 示例](#1.1)
  * [1.2 importBeanDefinitionResource](#1.2)
    * [1.2.1 判断路径](#1.2.1)
    * [1.2.2 处理绝对路径](#1.2.2)
    * [1.2.3 处理相对路径](#1.2.3)
* [2. alias 标签](#2)
  * [2.1 alias示例](#2.1)
  * [2.2 processAliasRegistration](#2.2)
  * [2.3 registerAlias](#2.3)
* [3. beans 标签](#3)

****

&nbsp;&nbsp; 前面分析了`<bean>`标签的解析，这是标签解析的核心逻辑。下面我们来看一下其他默认标签的解析过程。

<span id = "1"></span>
# 1. import 标签

<span id = "1.1"></span>
## 1.1 import 示例

&nbsp;&nbsp; 如果工程比较大，配置文件的维护会让人觉得恐怖，将所有的配置都放在一个 `spring.xml` 配置文件中，文件太多了

&nbsp;&nbsp; 所以针对这种情况 `Spring` 提供了一个分模块的思路，利用 `import` 标签，例如我们可以构造一个这样的 `spring.xml` 。

```java
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
       http://www.springframework.org/schema/beans/spring-beans.xsd">

    <import resource="spring-student.xml"/>

    <import resource="spring-student-dtd.xml"/>

</beans>
```

&nbsp;&nbsp; `spring.xml` 配置文件中，使用 `import` 标签的方式**导入其他模块的配置文件**。

- 如果有配置需要修改直接修改相应配置文件即可。
- 若有新的模块需要引入直接增加 `import` 即可。

&nbsp;&nbsp; 这样大大简化了配置后期维护的复杂度，同时也易于管理。

<span id = "1.2"></span>
## 1.2 importBeanDefinitionResource

 &nbsp;&nbsp; `Spring` 使用 `#importBeanDefinitionResource(Element ele)` 方法，完成对 `import` 标签的解析。 

```java
// org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java

/**
 * 解析 import 标签
 */
protected void importBeanDefinitionResource(Element ele) {
	// <1> 获取 resource 的属性值
	String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
	// 为空，直接退出
	if (!StringUtils.hasText(location)) {
		getReaderContext().error("Resource location must not be empty", ele);
		return;
	}

	// <2> 解析系统属性，格式如 ："${user.dir}"
	location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

	// 实际 Resource 集合，即 import 的地址，有哪些 Resource 资源
	Set<Resource> actualResources = new LinkedHashSet<>(4);

	// <3> 判断 location 是相对路径还是绝对路径
	boolean absoluteLocation = false;
	try {
		absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
	}
	catch (URISyntaxException ex) {
		// cannot convert to an URI, considering the location relative
		// unless it is the well-known Spring prefix "classpath*:"
	}

	if (absoluteLocation) {
		// <4> 绝对路径
		try {
			// 添加配置文件地址的 Resource 到 actualResources 中，并加载相应的 BeanDefinition 们
			int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
			if (logger.isDebugEnabled()) {
				logger.debug("Imported " + importCount + " bean definitions from URL location [" + location + "]");
			}
		}
		catch (BeanDefinitionStoreException ex) {
			getReaderContext().error(
					"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
		}
	}else {
		// <5> 相对路径
		try {
			int importCount;
			// 创建相对地址的 Resource
			Resource relativeResource = getReaderContext().getResource().createRelative(location);
			if (relativeResource.exists()) {
				/* 存在 */

				// 加载 relativeResource 中的 BeanDefinition 们
				importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
				// 添加到 actualResources 中
				actualResources.add(relativeResource);
			}else {
				/* 不存在 */

				// 获得根路径地址
				String baseLocation = getReaderContext().getResource().getURL().toString();
				// 添加配置文件地址的 Resource 到 actualResources 中，并加载相应的 BeanDefinition 们
				importCount = getReaderContext().getReader().loadBeanDefinitions(
						StringUtils.applyRelativePath(baseLocation, location), // 计算绝对路径
						actualResources);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Imported " + importCount + " bean definitions from relative location [" + location + "]");
			}
		}
		catch (IOException ex) {
			getReaderContext().error("Failed to resolve current resource location", ele, ex);
		}
		catch (BeanDefinitionStoreException ex) {
			getReaderContext().error("Failed to import bean definitions from relative location [" + location + "]",
					ele, ex);
		}
	}
	// <6> 解析成功后，进行监听器激活处理
	Resource[] actResArray = actualResources.toArray(new Resource[0]);
	getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
}
```

&nbsp;&nbsp; 解析 `import` 标签的过程较为清晰，整个过程如下：

* `<1> `处，获取 `source` 属性的值，**该值表示资源的路径**。
* `<2>` 处，解析路径中的系统属性，如 "${user.dir}" 。
* `<3>` 处，判断**资源路径 location 是绝对路径还是相对路径**。见 [「1.2.1 判断路径」 ](#1.2.1)
* `<4>` 处，如果是**绝对路径**，则**递归调用 `Bean` 的解析过程，进行另一次的解析**。见 [「1.2.2 处理绝对路径」 ](#1.2.2)
* `<5>` 处，如果是**相对路径**，则**先计算出绝对路径得到 Resource，然后进行解析**。见 [「1.2.3 处理相对路径」 ](#1.2.3)
* `<6>` 处，通知监听器，完成解析。

<span id = "1.2.1"></span>
### 1.2.1 判断路径

 &nbsp;&nbsp; 通过以下代码，来判断 `location` 是为相对路径还是绝对路径

```java
absoluteLocation = ResourcePatternUtils.isUrl(location) // <1>
    || ResourceUtils.toURI(location).isAbsolute(); // <2>
```

&nbsp;&nbsp; 判断绝对路径的规则如下：

- `<1>` 以 `classpath*:` 或者 `classpath:` 开头的为**绝对路径**。
- `<1>` 能够通过该 `location` 构建出 `java.net.URL` 为**绝对路径**。
- `<2>` 根据 `location` 构造 `java.net.URI` 判断调用 `#isAbsolute()` 方法，判断是否为**绝对路径**。

<span id = "1.2.2"></span>
### 1.2.2 处理绝对路径

 &nbsp;&nbsp; 如果 `location` 为绝对路径，则调用 `#loadBeanDefinitions(String location, Set actualResources)`， 方法。该方法在 `org.springframework.beans.factory.support.AbstractBeanDefinitionReader` 中定义

```java
// org.springframework.beans.factory.support.AbstractBeanDefinitionReader.java

public int loadBeanDefinitions(String location, @Nullable Set<Resource> actualResources) throws BeanDefinitionStoreException {
	// 获得 ResourceLoader 对象
	ResourceLoader resourceLoader = getResourceLoader();
	if (resourceLoader == null) {
		throw new BeanDefinitionStoreException(
				"Cannot import bean definitions from location [" + location + "]: no ResourceLoader available");
	}

	if (resourceLoader instanceof ResourcePatternResolver) {
		try {
			// 获得 Resource 数组，因为 Pattern 模式匹配下，可能有多个 Resource 。例如说，Ant 风格的 location
			Resource[] resources = ((ResourcePatternResolver) resourceLoader).getResources(location);
			// 加载 BeanDefinition 们
			int loadCount = loadBeanDefinitions(resources);
			// 添加到 actualResources 中
			if (actualResources != null) {
				for (Resource resource : resources) {
					actualResources.add(resource);
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Loaded " + loadCount + " bean definitions from location pattern [" + location + "]");
			}
			return loadCount;
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(
					"Could not resolve bean definition resource pattern [" + location + "]", ex);
		}
	}else {
		// 获得 Resource 对象
		Resource resource = resourceLoader.getResource(location);
		// 加载 BeanDefinition 们
		int loadCount = loadBeanDefinitions(resource);
		// 添加到 actualResources 中
		if (actualResources != null) {
			actualResources.add(resource);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Loaded " + loadCount + " bean definitions from location [" + location + "]");
		}
		return loadCount;
	}
}
```

&nbsp;&nbsp; 整个逻辑比较简单：

* 首先，获取 `ResourceLoader` 对象。
* 然后，根据不同的 `ResourceLoader` 执行不同的逻辑，主要是可能存在多个 `Resource` 。
* 最终，都会回归到 `XmlBeanDefinitionReader#loadBeanDefinitions(Resource... resources)` 方法，所以这是一个**递归**的过程。
* 另外，获得到的 `Resource` 的对象或数组，都会添加到 `actualResources` 中。

<span id = "1.2.3"></span>
### 1.2.3 处理相对路径

&nbsp;&nbsp; 如果 `location` 是相对路径，则会根据相应的 `Resource` 计算出相应的相对路径的 `Resource` 对象 ，然后：

* 若该 `Resource` 存在，则调用 `XmlBeanDefinitionReader#loadBeanDefinitions()` 方法，进行 `BeanDefinition` 加载。
* 否则，构造一个绝对 `location`( 即 `StringUtils.applyRelativePath(baseLocation, location)` 处的代码)，并调用 `#loadBeanDefinitions(String location, Set actualResources)` 方法，**与绝对路径过程一样**。

<span id = "2"></span>
# 2. alias 标签

<span id = "2.1"></span>
## 2.1 alias示例

&nbsp;&nbsp; 在对`bean`进行定义时，除了使用**`id`属性**来指定名称外，为了提供多个名称，可以使用`<alias>`标签来指定。**这些所有的名称都指向同一个`bean`**。

&nbsp;&nbsp; 要给`bean`指定别名，除了在定义时使用`name`属性外，还可以使用`<alias>`标签单独指定。

```java
<bean id="person" name="person1,person2" class="com.test.Person"/>
    
<alias name="person" alias="person3,person4"/> 
```

<span id = "2.2"></span>
## 2.2 processAliasRegistration

 &nbsp;&nbsp; `Spring` 使用 `#processAliasRegistration(Element ele)` 方法，完成对 `alias` 标签的解析。 

```java
// org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java

/**
 * 解析 alias 标签
 */
protected void processAliasRegistration(Element ele) {
	// 获取 name
	String name = ele.getAttribute(NAME_ATTRIBUTE);
	// 获取 alias
	String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
	boolean valid = true;
	if (!StringUtils.hasText(name)) {
		getReaderContext().error("Name must not be empty", ele);
		valid = false;
	}
	if (!StringUtils.hasText(alias)) {
		getReaderContext().error("Alias must not be empty", ele);
		valid = false;
	}
	if (valid) {
		try {
			// 注册 alias
			getReaderContext().getRegistry().registerAlias(name, alias);
		}
		catch (Exception ex) {
			getReaderContext().error("Failed to register alias '" + alias +
					"' for bean with name '" + name + "'", ele, ex);
		}
		// 别名注册后通知监听器做相应处理
		getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
	}
}
```

 &nbsp;&nbsp; 整个逻辑比较简单：

*  首先，**注册别名** 
*  然后 **通知监听器别名标签解析完毕** 

<span id = "2.3"></span>
## 2.3 registerAlias

&nbsp;&nbsp; `Spring`中调用`#registerAlias`来完成别名的注册

```java
// org.springframework.core.SimpleAliasRegistry.java

private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);

/**
 * 别名注册
 */
@Override
public void registerAlias(String name, String alias) {
	Assert.hasText(name, "'name' must not be empty");
	Assert.hasText(alias, "'alias' must not be empty");
	synchronized (this.aliasMap) {
		if (alias.equals(name)) {
			// 别名和实际名称一样，
			this.aliasMap.remove(alias);
			if (logger.isDebugEnabled()) {
				logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
			}
		}else {
			// 获取alias对应的已经注册的name
			String registeredName = this.aliasMap.get(alias);
			if (registeredName != null) {
				if (registeredName.equals(name)) {
					// 如果俩者相同，所以已经注册过，直接返回
					return;
				}
				if (!allowAliasOverriding()) {
					throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
							name + "': It is already registered for name '" + registeredName + "'.");
				}
				if (logger.isInfoEnabled()) {
					logger.info("Overriding alias '" + alias + "' definition for registered name '" +
							registeredName + "' with new target name '" + name + "'");
				}
			}
			// 否则，检查是否存在循环指向
			// 存在循环指向，则抛出异常
			checkForAliasCircle(name, alias);
			// 不存在循环指向，执行注册
			this.aliasMap.put(alias, name);
			if (logger.isDebugEnabled()) {
				logger.debug("Alias definition '" + alias + "' registered for name '" + name + "'");
			}
		}
	}
}
```

 &nbsp;&nbsp; 其中，检查循环指向的代码如下：

```java
// org.springframework.core.SimpleAliasRegistry.java

protected void checkForAliasCircle(String name, String alias) {
	if (hasAlias(alias, name)) {
		throw new IllegalStateException("Cannot register alias '" + alias +
				"' for name '" + name + "': Circular reference - '" +
				name + "' is a direct or indirect alias for '" + alias + "' already");
	}
}
```

 &nbsp;&nbsp; 由`#hasAlias`进行处理 

```java
// org.springframework.core.SimpleAliasRegistry.java

public boolean hasAlias(String name, String alias) {
	for (Map.Entry<String, String> entry : this.aliasMap.entrySet()) {
		String registeredName = entry.getValue();
		if (registeredName.equals(name)) {
			String registeredAlias = entry.getKey();
			if (registeredAlias.equals(alias) || hasAlias(registeredAlias, alias)) {
				return true;
			}
		}
	}
	return false;
}
```

<span id = "3"></span>
# 3. beans 标签

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
	   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	   xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
	
           
    <bean id="person6"  class="com.test.Person"/>

	<beans>
		<!-- 其他bean配置 -->
	</beans>
</beans>
```

&nbsp;&nbsp; `Spring`中调用`#doRegisterBeanDefinitions`来进行`beans`标签的解析,实际上就是递归调用之前注册`BeanDefinition`的方法。

