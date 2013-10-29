import org.quartz.*;
import org.quartz.impl.*;

public class WorkflowJob implements Job {
	public void execute(JobExecutionContext context) {
        	Workflow.logger.info("job run");
        }
}
