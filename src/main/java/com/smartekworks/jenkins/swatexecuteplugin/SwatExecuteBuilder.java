package com.smartekworks.jenkins.swatexecuteplugin;

import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link SwatExecuteBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #executeScript})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class SwatExecuteBuilder extends Builder {
	private final String executeScript;

	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
	@DataBoundConstructor
	public SwatExecuteBuilder(String executeScript) {
		this.executeScript = executeScript;
	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	public String getExecuteScript() {
		return executeScript;
	}


	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		// This is where you 'build' the project.
		// Since this is a dummy, we just say 'hello world' and call that a build.
		Utils utils = new Utils();
		String swatUrl = getDescriptor().getSwatUrl();
		String accessKey = getDescriptor().getAccessKey();
		String secretKey = getDescriptor().getSecretKey();
		long interval = getDescriptor().getInterval();
		interval = interval <= 0 ? 30000 : interval * 1000;

		JSONObject executionResult;
		ArrayList<String> completedList = new ArrayList<String>();

		try {
			JSONObject execIds = utils.apiPost(swatUrl + "/api/jenkins/execute?lang=ja", accessKey, secretKey, executeScript);

			while (true) {
				executionResult = utils.apiPost(swatUrl + "/api/jenkins/result?lang=ja", accessKey, secretKey, execIds.toString());
				JSONArray executions = executionResult.getJSONArray("executions");
				for (int i = 0; i < executions.size(); i++) {
					JSONObject execution = executions.getJSONObject(i);
					JSONArray completed = execution.getJSONArray("completed");
					for (int j = 0; j < completed.size(); j++) {
						JSONObject result = completed.getJSONObject(j);
						String id = execution.getString("id") + "-" + result.getString("id");
						if (!completedList.contains(id)) {
							completedList.add(id);
							SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
							Timestamp now = new Timestamp(System.currentTimeMillis());
							String timestamp = df.format(now);
							String message = timestamp + " " + execution.getString("title") + ":" + result.getString("caseTitle") +
									" 実行完了しました！ 結果：" + result.getString("status") + "（" + completedList.size() + "/" +
									executionResult.getInt("allCount") + "）";
							listener.getLogger().println(message);
						}
					}
				}

				if (executionResult.getString("status").equals("ended")) {
					break;
				}

				Thread.sleep(interval);
			}

			utils.createXmlFile(build.getWorkspace() + File.separator + "swat_result.xml", executionResult);
		} catch (Exception e) {
			listener.getLogger().println(e.getMessage());
			return false;
		}

		// This also shows how you can consult the global configuration of the builder
		return true;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}

	/**
	 * Descriptor for {@link SwatExecuteBuilder}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // This indicates to Jenkins that this is an implementation of an extension point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information,
		 * simply store it in a field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private String swatUrl;
		private String accessKey;
		private String secretKey;
		private int interval;

		/**
		 * In order to load the persisted global configuration, you have to
		 * call load() in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *      This parameter receives the value that the user has typed.
		 * @return
		 *      Indicates the outcome of the validation. This is sent to the browser.
		 *      <p>
		 *      Note that returning {@link FormValidation#error(String)} does not
		 *      prevent the form from being saved. It just means that a message
		 *      will be displayed to the user.
		 */
		public FormValidation doCheckExecuteScript(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set the request information");
			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "SWAT Execution";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			swatUrl = formData.getString("swatUrl");
			accessKey = formData.getString("accessKey");
			secretKey = formData.getString("secretKey");
			interval = formData.getInt("interval");
			// ^Can also use req.bindJSON(this, formData);
			//  (easier when there are many fields; need set* methods for this, like setUseFrench)
			save();
			return super.configure(req,formData);
		}

		/**
		 * This method returns true if the global configuration says we should speak French.
		 *
		 * The method name is bit awkward because global.jelly calls this method to determine
		 * the initial state of the checkbox by the naming convention.
		 */
		public String getSwatUrl() {
			return swatUrl;
		}

		public String getAccessKey() {
			return accessKey;
		}

		public String getSecretKey() {
			return secretKey;
		}

		public int getInterval() {
			return interval;
		}
	}
}
