/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.util.xml;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.io.*;

/**
 * Detects whether an XML stream is using DTD- or XSD-based validation.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 */
public class XmlValidationModeDetector {

	/**
	 * Indicates that the validation should be disabled.
	 */
	public static final int VALIDATION_NONE = 0;

	/**
	 * Indicates that the validation mode should be auto-guessed, since we cannot find
	 * a clear indication (probably choked on some special characters, or the like).
	 */
	public static final int VALIDATION_AUTO = 1;

	/**
	 * Indicates that DTD validation should be used (we found a "DOCTYPE" declaration).
	 */
	public static final int VALIDATION_DTD = 2;

	/**
	 * Indicates that XSD validation should be used (found no "DOCTYPE" declaration).
	 */
	public static final int VALIDATION_XSD = 3;


	/**
	 * The token in a XML document that declares the DTD to use for validation
	 * and thus that DTD validation is being used.
	 */
	private static final String DOCTYPE = "DOCTYPE";

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


	/**
	 * Detect the validation mode for the XML document in the supplied {@link InputStream}.
	 * Note that the supplied {@link InputStream} is closed by this method before returning.
	 * @param inputStream the InputStream to parse
	 * @throws IOException in case of I/O failure
	 * @see #VALIDATION_DTD
	 * @see #VALIDATION_XSD
	 */
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
				// <2>  hasOpeningTag 方法会校验，如果这一行有 "<" ，并且 "<" 后面跟着的是字母，则返回 true 。
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


	/**
	 * Does the content contain the DTD DOCTYPE declaration?
	 */
	private boolean hasDoctype(String content) {
		return content.contains(DOCTYPE);
	}

	/**
	 * Does the supplied content contain an XML opening tag. If the parse state is currently
	 * in an XML comment then this method always returns false. It is expected that all comment
	 * tokens will have consumed for the supplied content before passing the remainder to this method.
	 */
	private boolean hasOpeningTag(String content) {
		if (this.inComment) {
			return false;
		}
		int openTagIndex = content.indexOf('<');
		return (openTagIndex > -1 // "<" 存在
				&& (content.length() > openTagIndex + 1) // "<" 后面还有内容
				&& Character.isLetter(content.charAt(openTagIndex + 1))); // "<" 后面的内容是字母
	}

	/**
	 * Consumes all the leading comment data in the given String and returns the remaining content, which
	 * may be empty since the supplied content might be all comment data. For our purposes it is only important
	 * to strip leading comment content on a line since the first piece of non comment content will be either
	 * the DOCTYPE declaration or the root element of the document.
	 */
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

	/**
	 * 如果包含头标记，则返回头标记以后的内容；如果包含结束标记，则返回标记后面的内容。
	 */
	@Nullable
	private String consume(String line) {
		int index = (this.inComment ? endComment(line) : startComment(line));
		return (index == -1 ? null : line.substring(index));
	}

	/**
	 * Try to consume the {@link #START_COMMENT} token.
	 * @see #commentToken(String, String, boolean)
	 */
	private int startComment(String line) {
		return commentToken(line, START_COMMENT, true);
	}

	private int endComment(String line) {
		return commentToken(line, END_COMMENT, false);
	}

	/**
	 * Try to consume the supplied token against the supplied content and update the
	 * in comment parse state to the supplied value. Returns the index into the content
	 * which is after the token or -1 if the token is not found.
	 */
	private int commentToken(String line, String token, boolean inCommentIfPresent) {
		// 是否包含 token,如果包含则返回token所在的索引位置+token长度，如果没有包含则返回-1
		int index = line.indexOf(token);
		if (index > - 1) {
			this.inComment = inCommentIfPresent;
		}
		return (index == -1 ? index : index + token.length());
	}

}
