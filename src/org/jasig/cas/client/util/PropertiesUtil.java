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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
	protected final Logger logger = LoggerFactory.getLogger(getClass());
	
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
		System.out.println(getPropertiesValue("222"));
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
			Properties prop = new Properties();
			String path= PropertiesUtil.class.getClassLoader().getResource(File.separator).getPath();
			path = java.net.URLDecoder.decode(path);
			path=path.substring(0, path.length()-1);
			if(null == filelist || filelist.isEmpty()){
				filelist= getFiles(path);
			}
			for(File file:filelist){
				InputStream in = null;
				try {
					in = new FileInputStream(file);
					prop.load(in);
					value = prop.getProperty(name);
					if(StringUtils.isNotBlank(value)){
						casProperties.put(name, prop.getProperty(name));
						break;
					}
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}else{
			value = obj.toString();
		}
		return value;
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



