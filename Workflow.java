import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import org.quartz.*;
import org.quartz.impl.*;


import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

public class Workflow {
	static Logger logger = Logger.getLogger(Workflow.class);

	public static void main(String[] args) throws Exception {
		BasicConfigurator.configure();

		System.out.println("Starting Quartz");
		startWorkflow();
	}

	public static void startWorkflow() throws Exception {
		Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

		scheduler.start();

		JobDetail job = JobBuilder.newJob(WorkflowJob.class)
         		.withIdentity("job1", "group1")
             		.build();
        
		Trigger trigger = TriggerBuilder.newTrigger()
			.withIdentity("trigger1", "group1")
			.startNow()
			.withSchedule(SimpleScheduleBuilder.simpleSchedule()
			.withIntervalInSeconds(40)
			.repeatForever())            
			.build();
        
		scheduler.scheduleJob(job, trigger);

		Thread.sleep(60000);

		scheduler.shutdown();
	}
}
