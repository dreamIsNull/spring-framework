> 参考网址：<http://cmsblogs.com/?p=2763>

#### 目录

* [1. BeanDefinitionReaderUtils](#1)
* [2. BeanDefinitionRegistry](#2)
  * [2.1 通过 beanName 注册](#2.1)
  * [2.2 注册 alias 和 beanName 的映射](#2.2)

****

&nbsp;&nbsp;  `DefaultBeanDefinitionDocumentReader` 的 ﻿`#processBeanDefinition()` 方法，完成 `Bean` 标签解析的核心工作。 

```java
// org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java

/**
 * 进行 bean 标签解析
 */
protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
	/*
	 * <1> 委托BeanDefinitionParserDelegate进行解析
	 * 如果解析成功，则返回 BeanDefinitionHolder 对象。BeanDefinitionHolder 包含了配置文件配置的各种属性（id,name,class,alias）
	 * 如果解析失败，则返回 null
	 */
	BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
	if (bdHolder != null) {
		// <2> 进行自定义标签处理
		bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
		try {
			// <3> 进行 BeanDefinition 的注册
			BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
		}
		catch (BeanDefinitionStoreException ex) {
			getReaderContext().error("Failed to register bean definition with name '" +
					bdHolder.getBeanName() + "'", ele, ex);
		}
		// <4> 发出响应事件，通知相关的监听器，已完成该 Bean 标签的解析。
		getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
	}
}
```

*  解析工作分为三步 
  
  1. 解析默认标签
  
  2.  解析默认标签后下得自定义标签 
  
  3.  注册解析后的 `BeanDefinition`
*  经过前面两个步骤的解析，这时的 `BeanDefinition` 已经可以满足后续的使用要求了，**那么接下来的工作就是将这些 `BeanDefinition` 进行注册，也就是完成第三步** 

<span id ="1"></span>
# 1. BeanDefinitionReaderUtils

 &nbsp;&nbsp; 注册 `BeanDefinition` ，由 `BeanDefinitionReaderUtils.registerBeanDefinition()` 完成

```java
// org.springframework.beans.factory.support.BeanDefinitionReaderUtils.java

/**
 * 注册BeanDefinition
 */
public static void registerBeanDefinition(
		BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)
		throws BeanDefinitionStoreException {

	// 注册 beanName
	String beanName = definitionHolder.getBeanName();
	registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());

	// 注册 alias
	String[] aliases = definitionHolder.getAliases();
	if (aliases != null) {
		for (String alias : aliases) {
			registry.registerAlias(beanName, alias);
		}
	}
}
```

*  首先，通过 `beanName` 注册 `BeanDefinition` 。见 [2.1 通过 beanName 注册](#2.1)  
*  然后，再通过注册别名 `alias` 和 `beanName` 的映射。见 [2.2 注册 alias 和 beanName 的映射](#2.2) 

<span id ="2"></span>
# 2. BeanDefinitionRegistry

&nbsp;&nbsp;  `BeanDefinition` 的注册，由接口 `org.springframework.beans.factory.support.BeanDefinitionRegistry` 定义。 

<span id ="2.1"></span>
## 2.1 通过 beanName 注册

 &nbsp;&nbsp; 调用 `BeanDefinitionRegistry` 的 `#registerBeanDefinition(String beanName, BeanDefinition beanDefinition)` 方法，实现通过 `beanName` 注册 `BeanDefinition` 。 

```java
// org.springframework.beans.factory.support.DefaultListableBeanFactory.java

private boolean allowBeanDefinitionOverriding = true;

private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

private volatile List<String> beanDefinitionNames = new ArrayList<>(256);

private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);

@Nullable
private volatile String[] frozenBeanDefinitionNames;

@Override
public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
		throws BeanDefinitionStoreException {

	// 校验 beanName 与 beanDefinition 非空
	Assert.hasText(beanName, "Bean name must not be empty");
	Assert.notNull(beanDefinition, "BeanDefinition must not be null");

	// <1> 校验 BeanDefinition 。
	// 这是注册前的最后一次校验了，主要是对属性 methodOverrides 进行校验。
	if (beanDefinition instanceof AbstractBeanDefinition) {
		try {
			((AbstractBeanDefinition) beanDefinition).validate();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName,
					"Validation of bean definition failed", ex);
		}
	}

	// <3> 如果已经存在
	BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);

	if (existingDefinition != null) {
		// <3> 如果已经存在


		if (!isAllowBeanDefinitionOverriding()) {
			// 如果存在但是不允许覆盖，抛出异常
			throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName,
					"Cannot register bean definition [" + beanDefinition + "] for bean '" + beanName +
					"': There is already [" + existingDefinition + "] bound.");
		}else if (existingDefinition.getRole() < beanDefinition.getRole()) {
			// 覆盖 beanDefinition 大于 被覆盖的 beanDefinition 的 ROLE ，打印 info 日志
			if (logger.isWarnEnabled()) {
				logger.warn("Overriding user-defined bean definition for bean '" + beanName +
						"' with a framework-generated bean definition: replacing [" +
						existingDefinition + "] with [" + beanDefinition + "]");
			}
		}else if (!beanDefinition.equals(existingDefinition)) {
			// 覆盖 beanDefinition 与 被覆盖的 beanDefinition 不相同，打印 debug 日志
			if (logger.isInfoEnabled()) {
				logger.info("Overriding bean definition for bean '" + beanName +
						"' with a different definition: replacing [" + existingDefinition +
						"] with [" + beanDefinition + "]");
			}
		}else {
			// 其它，打印 debug 日志
			if (logger.isDebugEnabled()) {
				logger.debug("Overriding bean definition for bean '" + beanName +
						"' with an equivalent definition: replacing [" + existingDefinition +
						"] with [" + beanDefinition + "]");
			}
		}
		// 允许覆盖，直接覆盖原有的 BeanDefinition 到 beanDefinitionMap 中。
		this.beanDefinitionMap.put(beanName, beanDefinition);
	}else {
		// <4> 如果未存在

		// 检测创建 Bean 阶段是否已经开启，如果开启了则需要对 beanDefinitionMap 进行并发控制
		if (hasBeanCreationStarted()) {
			// beanDefinitionMap 为全局变量，避免并发情况
			synchronized (this.beanDefinitionMap) {
				// 添加到 BeanDefinition 到 beanDefinitionMap 中。
				this.beanDefinitionMap.put(beanName, beanDefinition);
				// 添加 beanName 到 beanDefinitionNames 中
				List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
				updatedDefinitions.addAll(this.beanDefinitionNames);
				updatedDefinitions.add(beanName);
				this.beanDefinitionNames = updatedDefinitions;
				// 从 manualSingletonNames 移除 beanName
				if (this.manualSingletonNames.contains(beanName)) {
					Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
					updatedSingletons.remove(beanName);
					this.manualSingletonNames = updatedSingletons;
				}
			}
		}else {
			// 添加到 BeanDefinition 到 beanDefinitionMap 中
			this.beanDefinitionMap.put(beanName, beanDefinition);
			// 添加 beanName 到 beanDefinitionNames 中
			this.beanDefinitionNames.add(beanName);
			// 从 manualSingletonNames 移除 beanName
			this.manualSingletonNames.remove(beanName);
		}
		this.frozenBeanDefinitionNames = null;
	}

	// <5> 重新设置 beanName 对应的缓存
	if (existingDefinition != null || containsSingleton(beanName)) {
		resetBeanDefinition(beanName);
	}
	else if (isConfigurationFrozen()) {
		clearByTypeCache();
	}
}
```

&nbsp;&nbsp;  处理过程如下 

*  `<1>` 对 `BeanDefinition` 进行校验，**该校验也是注册过程中的最后一次校验**了，主要是对 `AbstractBeanDefinition` 的 `methodOverrides` 属性进行校验 
*  `<2>` 根据 `beanName` **从缓存中获取 `BeanDefinition` 对象** 
*  `<3>` 如果缓存中存在，则根据 `allowBeanDefinitionOverriding` 标志来**判断是否允许覆盖**。如果允许则直接覆盖。否则，抛出 `BeanDefinitionStoreException` 异常 
*  `<4>` 若缓存中没有指定 `beanName` 的 `BeanDefinition`，则判断当前阶段是否已经开始了 `Bean` 的创建阶段？如果是，则需要对 `beanDefinitionMap` 进行加锁控制并发问题，否则直接设置即可 
*  `<5>` 若缓存中存在该 `beanName` 或者单例 `bean` 集合中存在该 `beanName` ，则调用 `#resetBeanDefinition(String beanName)` 方法，重置 `BeanDefinition` 缓存 

&nbsp;&nbsp;  整段代码的核心就在于 `this.beanDefinitionMap.put(beanName, beanDefinition);` 而 `BeanDefinition` 的缓存也不是神奇的东西，就是定义一个 `Map`:

*   `key` 为 `beanName` 
*  `value` 为 `BeanDefinition` 对象 

<span id ="2.2"></span>
## 2.2 注册 alias 和 beanName 的映射

&nbsp;&nbsp;  调用 `BeanDefinitionRegistry` 的 `#registerAlias(String name, String alias)` 方法，注册 `alias` 和 `beanName` 的映射关系 

```java
// org.springframework.core.SimpleAliasRegistry.java

/**
 * 别名Map
 * 	key: alias
 * 	value: beanName
 */
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
			// 别名和实际名称一样，则去掉alias
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
				// 不允许覆盖，则抛出 IllegalStateException 异常
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

*  注册 `alias` 和注册 `BeanDefinition` 的过程差不多 
*  在最后，调用了 `#checkForAliasCircle()` 来对别名进行了**循环**检测 

```java
// org.springframework.core.SimpleAliasRegistry.java

protected void checkForAliasCircle(String name, String alias) {
    if (hasAlias(alias, name)) {
        throw new IllegalStateException("Cannot register alias '" + alias +
                "' for name '" + name + "': Circular reference - '" +
                name + "' is a direct or indirect alias for '" + alias + "' already");
    }
}
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

&nbsp;&nbsp; 如果 `name`、`alias` 分别为 1 和 3 ，则构成 `（1,3）` 的映射。加入，此时集合中存在`（A,1）`、`（3,A）` 的映射，意味着出现循环指向的情况，则抛出 `IllegalStateException` 异常 