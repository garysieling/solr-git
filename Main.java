import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
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

public class Main {
	static int processed = 0;
	static long startTime = new Date().getTime();
	
	public static class InsertThread extends Thread
	{
		private final int _index;
		private final int _maxIndex;
		private File[] _files;
		private Collection<SolrInputDocument> _docs = new ArrayList<SolrInputDocument>();
		private HttpSolrServer _server;
		private Pattern _capitals = Pattern.compile(".*([A-Z]).*");

		DiffFormatter df = new DiffFormatter(
				DisabledOutputStream.INSTANCE);

		public InsertThread(int index, int maxIndex, File[] files)
		{
			_index = index;
			_maxIndex = maxIndex;
			_files = files;
		}
		
		public void run() 
		{
			_server = new HttpSolrServer(
					"http://localhost:8983/solr/");
			_server.setMaxRetries(5);
			
			int i = 0;
						
			for (File f : _files)
			{
				if (i % _maxIndex == _index)
				{
					String filename = f.getAbsolutePath() + "\\.git";
					
					System.out.println(filename);
					
					System.out.println("Total repositories: " + i);

					try 
					{
						convertRepo(filename);
					}
					catch (Error e)
					{
						System.out.println(e.getMessage());
					} catch (MalformedURLException e) {
						System.out.println(e.getMessage());
					} catch (AmbiguousObjectException e) {
						e.printStackTrace();
					} catch (MissingObjectException e) {
						e.printStackTrace();
					} catch (IncorrectObjectTypeException e) {
						e.printStackTrace();
					} catch (CorruptObjectException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (SolrServerException e) {
						e.printStackTrace();
					}
				}

				i++;
			}
		}
		

		private void convertRepo(String path)
				throws IOException, AmbiguousObjectException,
				MissingObjectException, IncorrectObjectTypeException,
				CorruptObjectException, SolrServerException {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();
			Repository repository = builder.setGitDir(new File(path)).build();
			
			RevWalk walk = new RevWalk(repository);

			Config storedConfig = repository.getConfig();
			Set<String> remotes = storedConfig.getSubsections("remote");
			 
			String remoteGithub = "";
			for (String remoteName : remotes) {
				String url = storedConfig.getString("remote", remoteName, "url");
				if (url.startsWith("https://github.com"))
				{
					if (!"".equals(remoteGithub))
					{
						System.out.println("Found second url - " + remoteGithub + "," + url);
					}
					remoteGithub = url.substring("https://github.com/".length());
					break;
				}
				else
				{
					System.out.println("Found non-github url:" + url);
				}
			}
			
			int batchSize = 10000;

			df.setRepository(repository);
			df.setContext(0);
			df.setDiffComparator(RawTextComparator.DEFAULT);
			df.setDetectRenames(true);
			
			boolean foundStart = false;
			for (Ref ref : repository.getAllRefs().values()) {
				try {
					if ("HEAD".equals(ref.getName())) {
						walk.markStart(walk.parseCommit(ref.getObjectId()));
						foundStart = true;
						break;
					}
				} catch (Exception notACommit) {
					System.out.println(notACommit.getMessage());
					continue;
				}
			}
			
			if (!foundStart) {
				System.out.println("Eror: could not find HEAD for " + path);
				return;
			}

			int cnt = 0;
			for (RevCommit commit : walk) {
				try {
					cnt++;
					StringBuffer search = new StringBuffer();

					SolrInputDocument document = new SolrInputDocument();
					if (commit.getParentCount() > 0) {
						RevCommit parent = walk.parseCommit(commit.getParent(0)
								.getId());

						java.util.List<DiffEntry> diffs = df.scan(parent.getTree(),
								commit.getTree());

						if (diffs.size() > 50) 
						{
							// we're aiming to find out who was the lead on a project
							// ignore massive merges / refactorings
							continue;
						}
						
						for (Object obj : diffs) {
							DiffEntry diff = (DiffEntry) obj;

							String file = diff.getNewPath().toLowerCase();

							ChangeType mode = diff.getChangeType();
							if (ChangeType.DELETE.equals(mode) || 
								ChangeType.RENAME.equals(mode) ||
								ChangeType.COPY.equals(mode)) {
								// since the aim is to find who was the lead on a project
								// just count things that look like real work, not moving
								// stuff around
								continue;
							}

							Matcher m = _capitals.matcher(file);
							String tokenizedFile = m.replaceAll(" \1");
							tokenizedFile = tokenizedFile.replace("/", " ");
							tokenizedFile = tokenizedFile.replace("_", " ");
							search.append(tokenizedFile);
						}		
					}
					
					PersonIdent commitAuthor = commit.getAuthorIdent();
					
					document.addField("id", remoteGithub + "." + commit.getId(), 1.0f);
					String author = commitAuthor.getName();
					author = author.replace(".", " ");
					
					document.addField("author", author);
					document.addField("email", nvl(commitAuthor.getEmailAddress(), " "));
					document.addField("company", getCompany(commitAuthor.getEmailAddress()));
					document.addField("date", commitAuthor.getWhen());
					document.addField("message", commit.getFullMessage());
					document.addField("name", commit.getName());
					document.addField("github", remoteGithub);

					search.append(" ").append(author);
					search.append(" ").append(commit.getFullMessage());

					document.addField("search", search.toString());
					_docs.add(document);

					if (cnt % batchSize == 0) {
						commitDocs();
					}
				} catch (Exception e) {
					System.out.println(e.getMessage());
					System.out.println(e);
				}
			}

			commitDocs();
		}

		private void commitDocs() {
			try {
				_server.add(_docs);
				_server.commit();

				synchronized (Main.class) {
					processed += _docs.size();
				}
			
				_docs = new ArrayList<SolrInputDocument>();
				logProgress();
			}
			catch (Exception e) {
				System.out.println(e);
			}
		}

		protected void logProgress() 
		{
			double elapsed = ( (new Date()).getTime() - startTime ) / 1000;
			double diffsPerSecond = processed / ( elapsed );
	        System.out.println("Commits per second:" + diffsPerSecond + ", elapsed time = " + elapsed + ", commits processed: " + processed + ", thread #" + _index);
		}
	}
	
	/**
	 * @param args
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws Exception {
		Date startDate = new Date();
		
		int maxThreads = Runtime.getRuntime().availableProcessors();
		
		File[] files = new File("E:\\VMs\\expert-search\\repos\\").listFiles();
		for (int i = 1; i <= maxThreads; i++)
		{
			System.out.println("Starting thread " + i + " threads");
			new InsertThread(i, maxThreads, files).start();
		}
		System.out.println("Starting " + maxThreads + " threads");
		
		Date endDate = new Date();
		
		System.out.println(startDate.toGMTString());
		System.out.println(endDate.toGMTString());
	}
	

	public static String nvl(String a, String b) {
		if (a == null) {
			return b;
		}
		
		return a;
	}

	private static String getCompany(String emailAddress) 
	{
		if (emailAddress == null)
		{
			emailAddress = "";
		}
		
		if (emailAddress.contains("@"))
		{
			String company = emailAddress.split("@")[1];
			if (company.contains("."))
			{
				company = company.substring(0, company.lastIndexOf("."));
			}
			
			if (company.contains("."))
			{
				int start = company.lastIndexOf(".");
				company = company.substring(start, company.length());
			}
			
			return company;
		}
		
		return emailAddress;
	}

}
