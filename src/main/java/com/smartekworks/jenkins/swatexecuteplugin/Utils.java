package com.smartekworks.jenkins.swatexecuteplugin;

import net.sf.json.JSONArray;
import org.apache.commons.codec.binary.Base64;
import net.sf.json.JSONObject;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class Utils {
	public JSONObject apiPost(String apiUrl, String accessKey, String secretKey, String body) throws Exception{
		JSONObject ret;

		URL url = new URL(apiUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");

		String authString = accessKey + ":" + secretKey;
		byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
		String authStringEnc = new String(authEncBytes);
		conn.setRequestProperty("Authorization", "Basic " + authStringEnc);

		OutputStream os = conn.getOutputStream();
		os.write(body.getBytes());
		os.flush();

		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new Exception("SWAT api call return false.");
		}

		BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

		StringBuilder result = new StringBuilder();
		String output;
		while ((output = br.readLine()) != null) {
			result.append(output);
		}

		conn.disconnect();

		ret = new JSONObject().fromObject(result.toString());

		return ret;
	}

	public void createXmlFile(String path, JSONObject executionResult) {
		try {
			OutputStream outputStream = new FileOutputStream(new File(path));

			XMLStreamWriter out = XMLOutputFactory.newInstance().createXMLStreamWriter(
					new OutputStreamWriter(outputStream, "utf-8"));

			out.writeStartDocument();
			out.writeStartElement("testsuites");
			out.writeAttribute("tests", String.valueOf(executionResult.getInt("allCount")));
			out.writeAttribute("name", "Executions");

			JSONArray executions = executionResult.getJSONArray("executions");
			for (int i = 0; i < executions.size(); i++) {
				JSONObject execution = executions.getJSONObject(i);
				JSONArray completed = execution.getJSONArray("completed");

				out.writeStartElement("testsuite");
				out.writeAttribute("id", execution.getString("id"));
				out.writeAttribute("tests", String.valueOf(completed.size()));
				out.writeAttribute("name", execution.getString("title"));

				for (int j = 0; j < completed.size(); j++) {
					JSONObject result = completed.getJSONObject(j);

					out.writeStartElement("testcase");
					out.writeAttribute("name", result.getString("caseTitle"));
					out.writeAttribute("status", result.getString("status"));
					out.writeAttribute("time", String.valueOf(result.getDouble("time")));
					if (!"".equals(result.getString("execError"))) {
						out.writeStartElement("error");
						out.writeAttribute("message", result.getString("execError"));
						out.writeEndElement();
					}
					out.writeEndElement();
				}

				out.writeEndElement();
			}

			out.writeEndElement();
			out.writeEndDocument();

			out.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
