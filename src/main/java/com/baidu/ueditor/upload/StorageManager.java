package com.baidu.ueditor.upload;

import com.baidu.ueditor.define.AppInfo;
import com.baidu.ueditor.define.BaseState;
import com.baidu.ueditor.define.State;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.ujigu.secure.common.bean.GlobalConstant;
import com.ujigu.secure.common.utils.HttpClientUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class StorageManager {
	public static final int BUFFER_SIZE = 8192;

	public StorageManager() {
	}

	public static State saveBinaryFile(byte[] data, String path) {
		File file = new File(path);

		State state = valid(file);

		if (!state.isSuccess()) {
			return state;
		}

		try {
			BufferedOutputStream bos = new BufferedOutputStream(
					new FileOutputStream(file));
			bos.write(data);
			bos.flush();
			bos.close();
		} catch (IOException ioe) {
			return new BaseState(false, AppInfo.IO_ERROR);
		}

		state = new BaseState(true, file.getAbsolutePath());
		state.putInfo( "size", data.length );
		state.putInfo( "title", file.getName() );
		return state;
	}

	public static State saveFileByInputStream(InputStream is, String path,
			long maxSize) {
		State state = null;

		File tmpFile = getTmpFile();

		byte[] dataBuf = new byte[ 2048 ];
		BufferedInputStream bis = new BufferedInputStream(is, StorageManager.BUFFER_SIZE);

		try {
			BufferedOutputStream bos = new BufferedOutputStream(
					new FileOutputStream(tmpFile), StorageManager.BUFFER_SIZE);

			int count = 0;
			while ((count = bis.read(dataBuf)) != -1) {
				bos.write(dataBuf, 0, count);
			}
			bos.flush();
			bos.close();

			if (tmpFile.length() > maxSize) {
				tmpFile.delete();
				return new BaseState(false, AppInfo.MAX_SIZE);
			}

			state = saveTmpFile(tmpFile, path);

			if (!state.isSuccess()) {
				tmpFile.delete();
			}

			return state;
			
		} catch (IOException e) {
		}
		return new BaseState(false, AppInfo.IO_ERROR);
	}
	
	public static byte[] getFileBytes(InputStream is) throws IOException{
		byte[] in2b = null;
		ByteArrayOutputStream swapStream = null;
		try{
			swapStream = new ByteArrayOutputStream();  
			byte[] buff = new byte[100];  
			int rc = 0;  
			while ((rc = is.read(buff, 0, 100)) > 0) {  
				swapStream.write(buff, 0, rc);  
			}  
			in2b = swapStream.toByteArray();  
		}finally {
			swapStream.close();
		}
	    return in2b; 
	}

	public static State saveFileByInputStream(InputStream is, String path) {
		State state = null;

		File tmpFile = getTmpFile();

		byte[] dataBuf = new byte[ 2048 ];
		BufferedInputStream bis = new BufferedInputStream(is, StorageManager.BUFFER_SIZE);

		try {
			BufferedOutputStream bos = new BufferedOutputStream(
					new FileOutputStream(tmpFile), StorageManager.BUFFER_SIZE);

			int count = 0;
			while ((count = bis.read(dataBuf)) != -1) {
				bos.write(dataBuf, 0, count);
			}
			bos.flush();
			bos.close();

			state = saveTmpFile(tmpFile, path);

			if (!state.isSuccess()) {
				tmpFile.delete();
			}

			return state;
		} catch (IOException e) {
		}
		return new BaseState(false, AppInfo.IO_ERROR);
	}
	
	/**
	 * 
	 * @param savePath 文件存储的相对uri路径
	 * @param content 文件的base64编码
	 * @return
	 */
	public static State remoteSave(String savePath, String content){
		Map<String, Object> params = new HashMap<>();
		params.put("file", savePath);
		params.put("base64Code", content);
		State storageState = null;
		try {
			String result = HttpClientUtils.httpPostRequest(GlobalConstant.UPFILE_DOMAIN + "/ueditor/upfile.do", params);
			if(StringUtils.isBlank(result)){
				storageState = new BaseState(false, AppInfo.IO_ERROR);
			}else{
				Map<String, Object> retMap = new Gson().fromJson(result, new TypeToken<Map<String, Object>>() {}.getType());
				if(retMap != null && "SUCCESS".equals(retMap.get("code"))){
					storageState = new BaseState(true);
					Object size = retMap.get("size");
					Object title = retMap.get("title");
					Object url = retMap.get("url");
					storageState.putInfo( "size", size == null ? 0 : Double.valueOf(size.toString()).longValue());
					storageState.putInfo( "title", title == null ? savePath : title.toString() );
					storageState.putInfo("url", url == null ? savePath : url.toString());
				}
			}
		} catch (UnsupportedEncodingException e) {
			storageState = new BaseState(false, AppInfo.REMOTE_FAIL);
		}
		
		return storageState;
	}

	private static File getTmpFile() {
		File tmpDir = FileUtils.getTempDirectory();
		String tmpFileName = (Math.random() * 10000 + "").replace(".", "");
		return new File(tmpDir, tmpFileName);
	}

	private static State saveTmpFile(File tmpFile, String path) {
		State state = null;
		File targetFile = new File(path);

		if (targetFile.canWrite()) {
			return new BaseState(false, AppInfo.PERMISSION_DENIED);
		}
		try {
			FileUtils.moveFile(tmpFile, targetFile);
		} catch (IOException e) {
			return new BaseState(false, AppInfo.IO_ERROR);
		}

		state = new BaseState(true);
		state.putInfo( "size", targetFile.length() );
		state.putInfo( "title", targetFile.getName() );
		
		return state;
	}

	private static State valid(File file) {
		File parentPath = file.getParentFile();

		if ((!parentPath.exists()) && (!parentPath.mkdirs())) {
			return new BaseState(false, AppInfo.FAILED_CREATE_FILE);
		}

		if (!parentPath.canWrite()) {
			return new BaseState(false, AppInfo.PERMISSION_DENIED);
		}

		return new BaseState(true);
	}
}
