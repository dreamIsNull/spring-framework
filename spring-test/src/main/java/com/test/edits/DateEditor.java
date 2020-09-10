package com.test.edits;



import java.beans.PropertyEditorSupport;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author : Mr-Z
 * @date : 2020/09/11 1:32
 */
public class DateEditor extends PropertyEditorSupport {

	private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public void setDateFormat() {
		System.out.println("1111111111111111");
		this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	}

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		try {
			Object value = dateFormat.parse(text);
			setValue(value);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getAsText() {
		if (getValue() instanceof Date) {
			Date d = (Date) getValue();
			return dateFormat.format(d);
		}
		return super.getAsText();
	}

}
