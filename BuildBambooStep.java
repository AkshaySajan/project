/**
 * The MIT License
 *
 * Copyright (c) 2017, LogMeIn, Inc.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * of the Software.  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.logmein.jenkins.plugins.pipeline.bamboo;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.permission.PermissionType;
import com.atlassian.bamboo.specs.api.builders.permission.Permissions;
import com.atlassian.bamboo.specs.api.builders.permission.PlanPermissions;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.PlanIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.AllOtherPluginsConfiguration;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.task.Task;
import com.atlassian.bamboo.specs.builders.task.CheckoutItem;
import com.atlassian.bamboo.specs.builders.task.MavenTask;
import com.atlassian.bamboo.specs.builders.task.ScriptTask;
import com.atlassian.bamboo.specs.builders.task.VcsCheckoutTask;
import com.atlassian.bamboo.specs.builders.trigger.RepositoryPollingTrigger;
import com.atlassian.bamboo.specs.util.BambooServer;
import com.atlassian.bamboo.specs.util.MapBuilder;
import com.atlassian.bamboo.specs.util.SimpleUserPasswordCredentials;
import com.atlassian.bamboo.specs.util.UserPasswordCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmein.jenkins.plugins.pipeline.bamboo.exceptions.BambooException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import jenkins.model.CauseOfInterruption;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BuildBambooStep extends Builder {
	
	private String finalTask;
    private String projectKey;
    private String projectName;
    private String repositories;
    private String jobKey;
    private String jobName;
    private String planName;
    private String planKey;
    private String serverAddress;
    private String username;
    private String password;
    private String task;

    @DataBoundConstructor
    public BuildBambooStep(String projectKey, String projectName, String repositories, String jobKey, String jobName,
                           String planKey, String planName,
                           String serverAddress,
                           String username,
                           String password, String finalTask, String task) {

        this.projectKey = projectKey;
        this.planKey = planKey;
        this.planName = planName;
        this.serverAddress = serverAddress;
        this.username = username;
        this.password = password;
        this.projectName = projectName;
        this.repositories = repositories;
        this.jobKey = jobKey;
        this.jobName = jobName;
        this.finalTask = finalTask;
        this.task = task;
       
    }

    /**
     * Project name getter
     * @return String that is the project key. Combined with plan to form the job name.
     */
    public String getProjectKey() {
        return projectKey;
    }

    public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getRepositories() {
		return repositories;
	}

	public void setRepositories(String repositories) {
		this.repositories = repositories;
	}

	public String getJobKey() {
		return jobKey;
	}

	public void setJobKey(String jobKey) {
		this.jobKey = jobKey;
	}

	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	public String getPlanName() {
		return planName;
	}

	public void setPlanName(String planName) {
		this.planName = planName;
	}
	
    public String getPlanKey() {
        return planKey;
    }

    public String getServerAddress() {
        return serverAddress;
    }


    public String getUsername() {
        return username;
    }

 
    public String getPassword() {
        return password;
    }
    

    public String getFinalTask() {
		return finalTask;
	}

	public void setFinalTask(String finalTask) {
		this.finalTask = finalTask;
	}
	
	public String gettask() {
		return finalTask;
	}

	public void settask(String task) {
		this.task = task;
	}
	
	


	@Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

    	@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// TODO Auto-generated method stub
			return true ;
		}


        @Override
        public String getDisplayName() {
            return "Create Build Bamboo";
        }
    }

  
       
        /**
         * Perform the actual work.  Start the job on Bamboo server, and then poll it until complete.
         * @return Returns null.
         */
        @Override
        public boolean perform(Build<?, ?> build, Launcher launcher, BuildListener listener)
    			throws InterruptedException, IOException {
            final String username = this.getUsername();
            final String password = this.getPassword();
            final String projectKey = this.getProjectKey();
            final String projectName = this.getProjectName();
            final String planName = this.getPlanName();
            final String repositories = this.getRepositories();
            final String jobKey = this.getJobKey();
            final String jobName = this.getJobName();
            final String planKey = this.getPlanKey();
            final String serverAddress = this.getServerAddress();
            final String finalTask = this.getFinalTask();
            final String task = this.gettask();
            UserPasswordCredentials adminUser = new SimpleUserPasswordCredentials(username, password);
            BambooServer bambooServer = new BambooServer(serverAddress,adminUser);
            Plan plan = createPlan(planName,planKey,projectName,projectKey,jobName,jobKey,repositories,finalTask,task);
            bambooServer.publish(plan);
            PlanPermissions planPermission = createPlanPermission(plan.getIdentifier(),username);
			bambooServer.publish(planPermission);
            try {
            	
            } catch (Exception e) {
            	System.out.println(e);
            }
            
            
            

            return true;
        }

        
        PlanPermissions createPlanPermission(PlanIdentifier planIdentifier, String username) {
    		Permissions permission = new Permissions()
    				.userPermissions(username, PermissionType.ADMIN, PermissionType.CLONE,
    						PermissionType.EDIT)
    				.groupPermissions("bamboo-admin", PermissionType.ADMIN).loggedInUserPermissions(PermissionType.ADMIN)
    				.anonymousUserPermissionView();
    		return new PlanPermissions(planIdentifier.getProjectKey(), planIdentifier.getPlanKey()).permissions(permission);
    	}

    	Project project(String projectName, String projectKey) {
    		return new Project().name(projectName).key(projectKey);
    	}
    	
    	static Task MavenTask() {
    		   MavenTask mavenTask = new MavenTask()
    				    .goal("clean install")
    				    .hasTests(false)
    				    .version3()
    				    .jdk("JDK 1.8")
    				    .executableLabel("Local_Maven");
    		   return mavenTask;
    	   }
    	static Task ScriptTask(){
    		ScriptTask script = new ScriptTask().inlineBody("hello this is Edgeops");
    		return script;
    		
    	}
    	
    	 static Task Taskcheck(String task){
    		if(task.equals("Maven")){
    			Task test = MavenTask();
    			return test;
    		}
    		else if(task.equals("Script")){
    			Task test = ScriptTask();
    			return test;
    		}
			return null;
			}
    	
    	   Job job(String jobName, String jobKey,String finalTask,String task){
    		   Job job = new Job(jobName, jobKey)
    				   .tasks(new VcsCheckoutTask().description("Checkout Default Repository").checkoutItems(new CheckoutItem().defaultRepository()).cleanCheckout(true))
    				   .tasks(new ScriptTask().inlineBody("echo Hello world!"))
    				   .tasks(Taskcheck(task))
    				   .finalTasks(new ScriptTask().inlineBody(finalTask));
    		   return job;
    	   }
    	   
    	 

		Plan createPlan(String planName, String planKey, String projectName,
    			   			String projectKey,String jobName,String jobKey,String repositories,String finalTask,String task) {

    			Plan Plan = new Plan(project(projectName,projectKey), planName, planKey).enabled(false).linkedRepositories(repositories)
    					.description("Edgeops Bamboo SNOW integration testing")
    					.stages(new Stage("Newstage").jobs(job(jobName,jobKey,finalTask,task)))		
    					.triggers(new RepositoryPollingTrigger().name("Repository polling"))
    					.planBranchManagement(new PlanBranchManagement().createForVcsBranch()
    					.delete(new BranchCleanup().whenRemovedFromRepositoryAfterDays(7)
    					.whenInactiveInRepositoryAfterDays(30))
    					.notificationForCommitters().issueLinkingEnabled(false))
    					.pluginConfigurations(new AllOtherPluginsConfiguration()
    					.configuration(new MapBuilder().put(repositories, -1).build()));
    			
    			return Plan;
    		}
        
    }


