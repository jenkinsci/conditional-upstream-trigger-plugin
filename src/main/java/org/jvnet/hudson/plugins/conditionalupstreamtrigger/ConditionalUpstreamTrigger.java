/*
 * The MIT License
 *
 * Copyright (c) 2010, Henrik Lynggaard
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.conditionalupstreamtrigger;

import static hudson.Util.fixNull;
import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.scheduler.CronTabList;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import java.util.List;
import java.util.logging.Logger;


import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import antlr.ANTLRException;
import hudson.model.Run;
import java.util.ArrayList;

/**
 * @author Henrik Lynggaard 
 */
public class ConditionalUpstreamTrigger extends Trigger<BuildableItem> {

    public static boolean debug = Boolean.getBoolean("MavenDependencyUpdateTrigger.debug");
    private static final Logger LOGGER = Logger.getLogger(ConditionalUpstreamTrigger.class.getName());
    private final String upstreamProjects;

    @DataBoundConstructor
    public ConditionalUpstreamTrigger(String cron_value, String upstreamProjects)
            throws ANTLRException {
        super(cron_value);
        this.upstreamProjects = upstreamProjects;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        boolean conditionMet = true;
        List<Run<?, ?>> buildsChecked = new ArrayList<Run<?, ?>>();

        try {
            LOGGER.config("upstreamProjects: " + upstreamProjects);

            // Check jobs
            for (String projectName : upstreamProjects.split(",")) {

                // find job
                AbstractProject<?, ?> upstreamJob = Hudson.getInstance().getItemByFullName(projectName, AbstractProject.class);
                if (upstreamJob == null) {
                    LOGGER.warning("Upstream job not found: " + projectName);
                    conditionMet = false;
                    continue;
                }

                // find last run
                Run<?, ?> lastBuild = upstreamJob.getLastBuild();
                if (lastBuild == null) {
                    LOGGER.info("Upstream project: " + projectName + " has not been built");
                    conditionMet = false;
                    continue;
                }
                buildsChecked.add(lastBuild);

                // check result
                Result result = lastBuild.getResult();
                LOGGER.info("Upstream project: " + lastBuild.getFullDisplayName() + " has status " + result);
                if (result.isWorseThan(Result.SUCCESS)) {
                    conditionMet = false;
                    continue;
                }
            }
            
            // trigger
            Cause cause = new ConditionalUpstreamTriggerCause(buildsChecked);
            if (conditionMet == true) {
                job.scheduleBuild(cause);
            }
        } catch (Exception e) {
            LOGGER.warning("ignore " + e.getMessage());
        } finally {
            long end = System.currentTimeMillis();
            LOGGER.info("time to run ConditionalUpStreamTrigger for project " + job.getName() + " : " + (end - start) + " ms");
        }
    }

    public String getUpstreamProjects() {
        return upstreamProjects;
    }

    @Extension
    public static class DescriptorImpl
            extends TriggerDescriptor {

        public boolean isApplicable(Item item) {
            return item instanceof BuildableItem;
        }

        public String getDisplayName() {
            return Messages.plugin_title();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/conditional-upstream-trigger/help.html";
        }

        /**
         * Performs syntax check.
         */
        public FormValidation doCheck(@QueryParameter String value) {
            try {
                String msg = CronTabList.create(fixNull(value)).checkSanity();
                if (msg != null) {
                    return FormValidation.warning(msg);
                }
                return FormValidation.ok();
            } catch (ANTLRException e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }
}
