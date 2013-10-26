import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.SolrServer;
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
		public InsertThread(int index, int maxIndex)
		{
			_index = index;
			_maxIndex = maxIndex;
		}
		
		public void run() 
		{
			try 
			{
				File[] files = new File("E:\\VMs\\expert-search\\repos\\").listFiles();
				HttpSolrServer server = new HttpSolrServer(
						"http://localhost:8983/solr/");

				int i = 0;
							
				for (File f : files)
				{
					if (i % _maxIndex == _index)
					{
						String filename = f.getAbsolutePath() + "\\.git";
						
						System.out.println(filename);
						convertRepo(server, filename);
					}
				}
				
				System.out.println("Total repositories: " + i);
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
	}
	
	/**
	 * @param args
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws Exception {
		Date startDate = new Date();
		
		int maxThreads = Runtime.getRuntime().availableProcessors();
		for (int i = 0; i < maxThreads; i++)
		{
			new InsertThread(i, maxThreads).start();
		}
		System.out.println("Starting " + maxThreads + " threads");
		
		Date endDate = new Date();
		
		System.out.println(startDate.toGMTString());
		System.out.println(endDate.toGMTString());
	}

	private static void convertRepo(SolrServer server, String path)
			throws IOException, AmbiguousObjectException,
			MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, SolrServerException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder.setGitDir(new File(path)).build();
		
		System.out.println("Loaded " + repository.getBranch() + " of " + path);

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
		}

		Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();

		Pattern capitals = Pattern.compile(".*([A-Z]).*");

		int cnt = 0;
		for (RevCommit commit : walk) {
			try {
				cnt++;
				StringBuffer search = new StringBuffer();

				SolrInputDocument doc1 = new SolrInputDocument();
				if (commit.getParentCount() > 0) {
					RevCommit parent = walk.parseCommit(commit.getParent(0)
							.getId());

					DiffFormatter df = new DiffFormatter(
							DisabledOutputStream.INSTANCE);
					df.setRepository(repository);
					df.setDiffComparator(RawTextComparator.DEFAULT);
					df.setDetectRenames(true);

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

						Matcher m = capitals.matcher(file);
						String tokenizedFile = m.replaceAll(" \1");
						tokenizedFile = tokenizedFile.replace("/", " ");
						tokenizedFile = tokenizedFile.replace("_", " ");
						search.append(tokenizedFile);
					}		
				}
				
				PersonIdent commitAuthor = commit.getAuthorIdent();
				
				doc1.addField("id", remoteGithub + "." + commit.getId(), 1.0f);
				String author = commitAuthor.getName();
				author = author.replace(".", " ");
				
				doc1.addField("author", author);
				doc1.addField("email", nvl(commitAuthor.getEmailAddress(), " "));
				doc1.addField("company", getCompany(commitAuthor.getEmailAddress()));
				doc1.addField("date", commitAuthor.getWhen());
				doc1.addField("message", commit.getFullMessage());
				doc1.addField("name", commit.getName());
				doc1.addField("github", remoteGithub);

				search.append(" ").append(author);
				search.append(" ").append(commit.getFullMessage());

				doc1.addField("search", search.toString());
				docs.add(doc1);

				if (cnt % batchSize == 0) {
					synchronized (Main.class) {
						processed += docs.size();
					}
					
					server.add(docs);

					server.commit();

					docs = new ArrayList<SolrInputDocument>();

					double elapsed = ( (new Date()).getTime() - startTime ) / 1000;
					double diffsPerSecond = processed / ( elapsed );
			        System.out.println("Commits per second:" + diffsPerSecond + ", elapsed time = " + elapsed + ", commits processed: " + processed);
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
				System.out.println(e);
			}
		}

		try {
			server.add(docs);
			server.commit();
			
			synchronized (Main.class) {
				processed += docs.size();
			}
			
			double elapsed = ( (new Date()).getTime() - startTime ) / 1000;
			double diffsPerSecond = processed / ( elapsed );
	        System.out.println("Commits per second:" + diffsPerSecond + ", elapsed time = " + elapsed + ", commits processed: " + processed);
		}
		catch (Exception e)
		{
			System.out.println(e.getMessage());
		}

		server.commit();
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
