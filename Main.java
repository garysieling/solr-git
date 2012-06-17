import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

@SuppressWarnings("deprecation")
public class Main {
	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		SolrServer server = new CommonsHttpSolrServer(
				"http://localhost:8080/solr/");
		server.deleteByQuery("*:*");

		System.out.println("Loading repository");

		convertRepo(server, "/Users/gary/REPOSITORY.git/.git");
	}

	private static void convertRepo(SolrServer server, String path)
			throws IOException, AmbiguousObjectException,
			MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, SolrServerException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder.setGitDir(new File(path))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build();
		System.out.println("Loaded " + repository.getBranch() + " of " + path);

		ObjectId head = repository.resolve("HEAD");
		System.out.println("head: " + head);

		RevWalk walk = new RevWalk(repository);

		System.out.println("Iterating " + walk.getRevFilter().toString());

		for (Ref ref : repository.getAllRefs().values()) {
			try {
				walk.markStart(walk.parseCommit(ref.getObjectId()));
			} catch (InvalidObjectException notACommit) {
				continue;
			}
		}

		Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();

		System.out.println(walk.iterator().hasNext());
		Pattern jiraId = Pattern.compile(".*(JIRA-\\d*).*");
		Pattern capitals = Pattern.compile(".*([A-Z]).*");

		int cnt = 0;
		for (RevCommit commit : walk) {
			cnt++;
			StringBuffer search = new StringBuffer();

			SolrInputDocument doc = new SolrInputDocument();
			if (commit.getParentCount() > 0) {
				RevCommit parent = walk
						.parseCommit(commit.getParent(0).getId());

				DiffFormatter df = new DiffFormatter(
						DisabledOutputStream.INSTANCE);
				df.setRepository(repository);
				df.setDiffComparator(RawTextComparator.DEFAULT);
				df.setDetectRenames(true);

				List<DiffEntry> diffs = df.scan(parent.getTree(),
						commit.getTree());

				String[] projects = new String[diffs.size()];
				String[] filetypes = new String[diffs.size()];

				int i = 0;
				for (Object obj : diffs) {
					DiffEntry diff = (DiffEntry) obj;

					String file = diff.getNewPath().toLowerCase();
					//String fullName = diff.getNewPath();

					ChangeType mode = diff.getChangeType();
					if (ChangeType.DELETE.equals(mode)) {
						file = diff.getOldPath().toLowerCase();
						//fullName = diff.getOldPath();
					}

					projects[i] = "";
					if (file.indexOf("/") >= 0) {
						projects[i] = file.substring(0, file.indexOf("/"));

						search.append(" ");
						search.append(projects[i].replace("_", " "));
						search.append(" ");
						search.append(projects[i]);
					}

					filetypes[i] = "";
					if (file.lastIndexOf(".") >= 0) {
						filetypes[i] = file.substring(file.lastIndexOf("."));
					}

					search.append(" ");
					search.append(file);

					Matcher m = capitals.matcher(file);
					String fileStuff = m.replaceAll(" \1");
					fileStuff = fileStuff.replace("/", " / ");

					/*
					 * FileHeader header = df.toFileHeader(diff);
					 *
					 * java.util.List changes = (java.util.List)
					 * header.getHunks(); int add = 0; int mod = 0; int del = 0;
					 * 
					 * for (Object o : changes) { HunkHeader hunk =
					 * (HunkHeader)o; if (ChangeType.ADD.equals(mode)) { add +=
					 * (hunk.getNewLineCount()); // System.out.println("mod:" +
					 * add); } else if (ChangeType.MODIFY.equals(mode)) { mod +=
					 * (hunk.getNewLineCount()); // System.out.println("mod:" +
					 * mod); } }
					 */

					i++;
				}

				doc.addField("project", projects);
				doc.addField("filetype", filetypes);
			}
			doc.addField("id", commit.getId(), 1.0f);
			String author = commit.getAuthorIdent().getName();
			author = author.replace(".", " ");

			// converting several repositories into the full text index -
			// not all repositories have the same format for usernames
			author = author.replace("gsieling", "Gary Sieling"); 

			doc.addField("author", author, 1.0f);
			doc.addField("email", commit.getAuthorIdent().getEmailAddress(), 1.0f);
			doc.addField("date", commit.getAuthorIdent().getWhen(), 1.0f);
			doc.addField("message", commit.getFullMessage(), 1.0f);
			doc.addField("name", commit.getName(), 1.0f);

			search.append(" ").append(author);
			search.append(" ").append(commit.getFullMessage());

			doc.addField("search", search.toString());

			String msg = commit.getFullMessage();
			doc.addField("ide",
					msg.toLowerCase().contains("intellij") ? "intellij": "eclipse", 1.0f);
			Matcher m = jiraId.matcher(msg);

			if (m.find()) {
				String issueId = m.group(1);
				doc.addField("jira", issueId, 1.0f);
			}
			docs.add(doc);

			if (cnt % 100 == 0) {
				server.add(docs);
				server.commit();

				docs = new ArrayList<SolrInputDocument>();
				System.out.println("Committed batch");
			}
		}

		server.add(docs);

		server.commit();
		System.out.println("finished");
	}
}
