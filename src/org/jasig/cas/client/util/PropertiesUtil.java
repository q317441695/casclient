/*类名：配置文件帮助类*/
/**
 * @author 崔雪峰
 * @date 2015-05-06
 * 备注：新建
 */
package org.jasig.cas.client.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配置文件帮助类
 */
public class PropertiesUtil {
	
	/**
	 * 日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(PropertiesUtil.class);
	
	/**
	 * cas 配置內容緩存
	 */
	private static Map<String,Object> casProperties = new HashMap<String,Object>();

	/**
	 * 配置文件列表
	 */
	private static ArrayList<File> filelist = new ArrayList<File>();
    
	/**
	 * 测试方法
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println(getPropertiesValue("nidaye"));
	}

	/**
	 * 私有构造方法
	 */
	private PropertiesUtil() {}
	/**
	 * 获取读取的参数值
	 * @return
	 */
	public static String getPropertiesValue(String name){
		String value = "";
		Object obj = "";
		if(null != casProperties){
			obj = casProperties.get(name);
		}
		if(null == obj){
			String path= PropertiesUtil.class.getClassLoader().getResource(File.separator).getPath();
			try {
				path = java.net.URLDecoder.decode(path,"utf-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			path=path.substring(0, path.length()-1);
			if(null == filelist || filelist.isEmpty()){
				filelist= getFiles(path);
			}
			value = getValueFromProperties(name);
		}else{
			value = obj.toString();
		}
		return value;
	}
	
	/**
	 * 从配置文件中获取值
	 * @param value
	 * @param name
	 * @return
	 */
	private static String getValueFromProperties(String name){
		String value = "";
		Properties prop = null;
		for(File file:filelist){
			prop = new Properties();
			InputStream in = null;
			try {
				in = new FileInputStream(file);
				prop.load(in);
				value = prop.getProperty(name);
				if(StringUtils.isNotBlank(value)){
					//存在匹配
					if(matcherValue(value)){
						//匹配可以
						String key = getReplaceKey(value);
						Object object = casProperties.get(key);
						String replaceValue = "";
						if(null == object){
							replaceValue = getReplaceKeyValue(key);
						}else{
							replaceValue = object.toString();
						}
						if(StringUtils.isBlank(replaceValue)){
							CommonUtils.assertNotNull(replaceValue, key + "'s value cannot be null.");
						}
						value = value.replaceAll("\\$\\{"+key+"\\}", replaceValue);
					}
					casProperties.put(name, value);
					break;
				}
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return value;
	}
	
	/**
	 * 获取替代的值
	 * @param key
	 * @return
	 */
	private static String getReplaceKeyValue(String key){
		String value = null;
		Properties prop = null;
		for(File file:filelist){
			prop = new Properties();
			InputStream in = null;
			try {
				in = new FileInputStream(file);
				prop.load(in);
				value = prop.getProperty(key);
				if(null != value){
					break;
				}
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return value;
	}
	
	/**
	 * 获取替代的key
	 * @param value 值
	 * @return 字符串
	 */
	private static String getReplaceKey(String value){
		int start = value.indexOf("${");
		int end = value.indexOf("}");
		String name = value.substring(start+2, end);
		return name;
	}
	
	/**
	 * 判断是否匹配
	 * @param value 匹配对象
	 * @return true or fasle
	 */
	private static boolean matcherValue(String value){
		Pattern p = Pattern.compile("(\\S+|\\S?)\\$\\{\\S+\\}(\\S+|\\S?)");
		Matcher m = p.matcher(value);
		return m.matches();
	}

	/**
	 * 通过递归得到某一路径下所有的目录及其文件
	*/
	private static ArrayList<File> getFiles(String filePath){
		File root = new File(filePath);
		File[] files = root.listFiles();
		for(File file:files){
			if(file.isDirectory()){
				getFiles(file.getPath());
			}else{
				if(file.getName().endsWith(".properties")){
					filelist.add(file);
				}
			}
		}
		return filelist;
	}
}



