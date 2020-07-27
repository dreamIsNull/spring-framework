> 参考网址:<http://cmsblogs.com/?p=2688>

#### 目录

* [1. DTD 与 XSD](#1)
  * [1.1 DTD](#1.1)
  * [1.2 XSD](#1.2)
* [2. getValidationModeForResource](#2)
* [3. XmlValidationModeDetector](#3)
* [4.  consumeCommentTokens](#4)

****

&nbsp;&nbsp; 在[【Spring 5.0.x】—— 3. IoC 之加载 BeanDefinition]()中提到，核心逻辑方法 `#doLoadBeanDefinitions(InputSource inputSource, Resource resource)` 方法，主要是做三件事情：

1. 调用 `#getValidationModeForResource(Resource resource)` 方法，获取指定资源（`xml`）的**验证模式**。
2. 调用 `DocumentLoader#loadDocument(InputSource inputSource, EntityResolver entityResolver,ErrorHandler errorHandler, int validationMode, boolean namespaceAware)` 方法，获取 `XML Document `实例。
3. 调用 `#registerBeanDefinitions(Document doc, Resource resource)` 方法，根据获取的 `Document` 实例，注册 `BeanDefinition` 信息。

&nbsp;&nbsp; 这里分析第一步： 分析获取 `xml` 文件的验证模式 。

&nbsp;&nbsp; 为什么需要获取验证模式呢？  **XML 文件的验证模式保证了 XML 文件的正确性。**

<span id="1"></span>
# 1. DTD 与 XSD

<span id="1.1"></span>
## 1.1 DTD

 &nbsp;&nbsp; `DTD`(`Document Type Definition`)，即**文档类型定义**，为 `XML`文件的验证机制，属于 `XML` 文件中组成的一部分。`DTD` 是一种保证 `XML` 文档格式正确的有效验证方式，它定义了相关 `XML` 文档的**元素、属性、排列方式、元素的内容类型以及元素的层次结构**。其实 `DTD` 就相当于 `XML` 中的 “词汇”和“语法”，我们可以通过比较 `XML` 文件和 `DTD` 文件 来看文档是否符合规范，元素和标签使用是否正确。

&nbsp;&nbsp;  要使用 `DTD`，需要在 `Spring XML` 文件头部声明： 

```java
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE beans PUBLIC  "-//SPRING//DTD BEAN//EN"  "http://www.springframework.org/dtd/spring-beans.dtd">
<beans>
	...    
</beans>
```

&nbsp;&nbsp; `DTD` 在一定的阶段推动了 `XML` 的发展，但是它本身存在着一些**缺陷**：

1. 它没有使用 `XML` 格式，而是**自己定义了一套格式**，相对**解析器的重用性较差**；而且 `DTD` 的构建和访问没有标准的编程接口，因而解析器很难简单的解析 `DTD` 文档。
2. `DTD` 对元素的类型限制较少；同时其他的约束力也叫弱。
3. `DTD` 扩展能力较差。
4. 基于正则表达式的 `DTD` 文档的描述能力有限。

<span id="1.2"></span>
## 1.2 XSD

&nbsp;&nbsp; 针对 `DTD` 的缺陷，`W3C` 在 2001 年推出 `XSD`。`XSD`（`XML Schemas Definition`）即 `XML Schema` 语言。`XML Schema` 本身就是一个 `XML`文档，使用的是 `XML` 语法，因此可以很方便的解析 `XSD` 文档。相对于 `DTD`，`XSD` 具有如下**优势**：

1. `XML Schema` 基于 `XML `，没有专门的语法。
2. `XML Schema` 可以象其他 `XML` 文件一样解析和处理。
3. `XML Schema` 比 `DTD` 提供了**更丰富的数据类型**。
4. `XML Schema` 提供**可扩充的数据模型**。
5. `XML Schema` 支持**综合命名空间**。
6. `XML Schema` 支持**属性组**。

&nbsp;&nbsp; 要使用`XSD`，除了要声明**名称空间**外（`xmlns="http://www.springframework.org/schema/beans"`），还必须指定名称空间所对应的`XML Schema`文档的**存储位置**。通过`schemaLocation`属性来**指定名称空间所对应的存储位置**。它包含两部分，一部分是名称空间的`URI`，另一部分就是该名称空间所对应的`XML Schema`文件位置或`URL`地址（`xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd"`）

```java
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
	   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	   xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
	
	...
</beans>
```

<span id="2"></span>
# 2. getValidationModeForResource

&nbsp;&nbsp; 了解了`DTD`与`XSD`后，对代码中的提取验证模式就更容易理解了，`Spring`中通过`#getValidationModeForResource`方法来获取对应资源的验证模式。

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

/**
 * 禁用验证模式
 */
public static final int VALIDATION_NONE = XmlValidationModeDetector.VALIDATION_NONE;

/**
 * 自动获取验证模式
 */
public static final int VALIDATION_AUTO = XmlValidationModeDetector.VALIDATION_AUTO;

/**
 * DTD 验证模式
 */
public static final int VALIDATION_DTD = XmlValidationModeDetector.VALIDATION_DTD;

/**
 * XSD 验证模式
 */
public static final int VALIDATION_XSD = XmlValidationModeDetector.VALIDATION_XSD;
/**
 * 验证模式。默认为自动模式。
 */
private int validationMode = VALIDATION_AUTO;


/**
 * 获取Resource的验证模式
 * @param resource
 * @return
 */
protected int getValidationModeForResource(Resource resource) {
	// <1> 获取指定的验证模式
	int validationModeToUse = getValidationMode();
	// 首先，如果手动指定，则直接返回
	if (validationModeToUse != VALIDATION_AUTO) {
		return validationModeToUse;
	}
	// <2> 其次，自动获取验证模式
	int detectedMode = detectValidationMode(resource);
	if (detectedMode != VALIDATION_AUTO) {
		return detectedMode;
	}
	
	// <3> 最后，使用 VALIDATION_XSD 做为默认
	return VALIDATION_XSD;
}
```

* `<1>` 处，调用 `#getValidationMode()` 方法，获取指定的验证模式( `validationMode` )。如果有手动指定（`XmlBeanDefinitionReader#setValidationMode`），则直接返回。另外，对于 `validationMode` 属性的设置和获得的代码，代码如下： 

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

public void setValidationMode(int validationMode) {
	this.validationMode = validationMode;
}

public int getValidationMode() {
	return this.validationMode;
}
```

> 可以通过`XmlBeanDefinitionReader#setValidationMode`方法手动设置验证模式

* `<2>` 处，调用 `#detectValidationMode(Resource resource)` 方法，自动获取验证模式。代码如下： 

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

/**
 * XML 验证模式探测器
 */
private final XmlValidationModeDetector validationModeDetector = new XmlValidationModeDetector();

/**
 * 获取Resource验证模式
 */
protected int detectValidationMode(Resource resource) {

	// 不可读，抛出 BeanDefinitionStoreException 异常
	if (resource.isOpen()) {
		throw new BeanDefinitionStoreException(
				"Passed-in Resource [" + resource + "] contains an open stream: " +
				"cannot determine validation mode automatically. Either pass in a Resource " +
				"that is able to create fresh streams, or explicitly specify the validationMode " +
				"on your XmlBeanDefinitionReader instance.");
	}

	// 打开 InputStream 流
	InputStream inputStream;
	try {
		inputStream = resource.getInputStream();
	}
	catch (IOException ex) {
		throw new BeanDefinitionStoreException(
				"Unable to determine validation mode for [" + resource + "]: cannot open InputStream. " +
				"Did you attempt to load directly from a SAX InputSource without specifying the " +
				"validationMode on your XmlBeanDefinitionReader instance?", ex);
	}

	try {
		// <x> 获取相应的验证模式(委托给XmlValidationModeDetector#detectValidationMode)
		return this.validationModeDetector.detectValidationMode(inputStream);
	}
	catch (IOException ex) {
		throw new BeanDefinitionStoreException("Unable to determine validation mode for [" +
				resource + "]: an error occurred whilst reading from the InputStream.", ex);
	}
}
```

 &nbsp;&nbsp; 核心在于 `<x>`处，调用 `XmlValidationModeDetector#detectValidationMode(InputStream inputStream)` 方法，获取相应的验证模式。

*   `<3>` 处，使用 `VALIDATION_XSD` （`XSD`）做为默认的验证模式。

<span id="3"></span>
# 3. XmlValidationModeDetector

 &nbsp;&nbsp; `org.springframework.util.xml.XmlValidationModeDetector` ，`XML` 验证模式探测器。 

```java
// org.springframework.util.xml.XmlValidationModeDetector.java

public int detectValidationMode(InputStream inputStream) throws IOException {
	// 读取传入的流文件
	BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
	try {
		// 是否为 DTD 校验模式。默认为，非 DTD 模式，即 XSD 模式
		boolean isDtdValidated = false;
		String content;
		// <0> 循环，逐行读取 XML 文件的内容
		while ((content = reader.readLine()) != null) {
      
            // 这里进行注释的处理
			content = consumeCommentTokens(content);

			// 如果是注释，或者空行则跳过
			if (this.inComment || !StringUtils.hasText(content)) {
				continue;
			}

			// <1> 包含 DOCTYPE 为 DTD 模式
			if (hasDoctype(content)) {
				isDtdValidated = true;
				break;
			}
			// <2>  hasOpeningTag 方法会校验，如果这一行有 < ，并且 < 后面跟着的是字母，则返回 true 。
			if (hasOpeningTag(content)) {
				break;
			}
		}
		// 返回 VALIDATION_DTD or VALIDATION_XSD 模式
		return (isDtdValidated ? VALIDATION_DTD : VALIDATION_XSD);
	}
	catch (CharConversionException ex) {
		// <3> 返回 VALIDATION_AUTO 模式
		return VALIDATION_AUTO;
	}
	finally {
		// 关闭输入流
		reader.close();
	}
}

```

- `<0`> 处，主要是通过**逐行**读取 `XML` 文件的内容，来进行判断。
- `<1>` 处，调用 `#hasDoctype(String content)` 方法，判断内容中如果包含有 `"DOCTYPE`“ ，则为 `DTD` 验证模式。代码如下：

```java
// org.springframework.util.xml.XmlValidationModeDetector.java

private static final String DOCTYPE = "DOCTYPE";

private boolean hasDoctype(String content) {
	return content.contains(DOCTYPE);
}
```

* `<2>` 处，调用 `#hasOpeningTag(String content)` 方法，**判断如果这一行包含 `<` ，并且 `<` 紧跟着的是字母，则为 `XSD` 验证模式**。代码如下： 

```java
// org.springframework.util.xml.XmlValidationModeDetector.java

private boolean hasOpeningTag(String content) {
	if (this.inComment) {
		return false;
	}
	int openTagIndex = content.indexOf('<');
	return (openTagIndex > -1 // "<" 存在
			&& (content.length() > openTagIndex + 1) // "<" 后面还有内容
			&& Character.isLetter(content.charAt(openTagIndex + 1))); // "<" 后面的内容是字母
}
```

* `<3>` 处，如果发生 `CharConversionException` 异常，则为 `VALIDATION_AUTO` 模式。 

<span id="4"></span>
# 4.  consumeCommentTokens

&nbsp;&nbsp; `org.springframework.util.xml.XmlValidationModeDetector#consumeCommentTokens(String line)`,这个方法用来处理`XML`文件注释。

```java
// org.springframework.util.xml.XmlValidationModeDetector.java

/**
 * XML 注释开始
 */
private static final String START_COMMENT = "<!--";

/**
 * XML 注释结束
 */
private static final String END_COMMENT = "-->";

/**
 * 当前状态是否在注释语句
 */
private boolean inComment;

@Nullable
private String consumeCommentTokens(String line) {

	//START_COMMENT = "<!--"; END_COMMENT = "-->"
	// <1> 如果是普通的语句就直接返回（不包含"<!--"、"-->"）
	if (!line.contains(START_COMMENT) && !line.contains(END_COMMENT)) {
		return line;
	}
	String currLine = line;
    
    // <2> 循环处理，去掉注释
	while ((currLine = consume(currLine)) != null) {
		if (!this.inComment && !currLine.trim().startsWith(START_COMMENT)) {
             // 当前状态不在注释语句且不以"<!--"开始
			return currLine;
		}
	}
	return null;
}
```

* 首先 用`boolean inComment`标记用来标记当前状态是否在注释语句内 
* `<1>`处，判断是否是普通语句(不包含`"<!--"`、`"-->"`)，如果是则返回，不是，则进入后续处理
* `<2>`处，调用`#consume`循环处理，去掉注释，里面判断**当前状态不在注释语句(`inComment=false`)且不以`"<!--"`开始**就返回。

```java
// org.springframework.util.xml.XmlValidationModeDetector.java

/**
 * 如果包含头标记，则返回头标记以后的内容；如果包含结束标记，则返回标记后面的内容。
 */
@Nullable
private String consume(String line) {
	int index = (this.inComment ? endComment(line) : startComment(line));
	return (index == -1 ? null : line.substring(index));
}

private int startComment(String line) {
	return commentToken(line, START_COMMENT, true);
}

private int endComment(String line) {
	return commentToken(line, END_COMMENT, false);
}


private int commentToken(String line, String token, boolean inCommentIfPresent) {
	// 是否包含 token,如果包含则返回token所在的索引位置+token长度，如果没有包含则返回-1
    int index = line.indexOf(token);
	if (index > - 1) {
		this.inComment = inCommentIfPresent;
	}
	return (index == -1 ? index : index + token.length());
}
```

*  刚开始碰到一个注释语句，会执行`startComment(line)`，`inComment`变为`true`，返回一个剔除`"<! --"`及之前内容的`String`，回到`consumeCommentTokens`方法，`while`循环继续。
*  接着会执行`endComment(line)`，
  * 如果注释结束则`inComment`变为`false`，返回一个空`String`，该空`String`会被返回给`consumeCommentTokens`方法，然后被忽略掉；
  * 如果注释没结束，即没检测到
    `“-->”`，`inCommen`仍为`true `