package com.redhat.gss.redhat_support_lib.infrastructure;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.mail.internet.MimeUtility;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTPClient;

import com.redhat.gss.redhat_support_lib.errors.FTPException;
import com.redhat.gss.redhat_support_lib.errors.RequestException;
import com.redhat.gss.redhat_support_lib.helpers.FilterHelper;
import com.redhat.gss.redhat_support_lib.helpers.QueryBuilder;
import com.redhat.gss.redhat_support_lib.parsers.Attachment;
import com.redhat.gss.redhat_support_lib.parsers.Comment;
import com.redhat.gss.redhat_support_lib.web.ConnectionManager;

public class Attachments extends BaseQuery {
	ConnectionManager connectionManager = null;

	public Attachments(ConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
	}

	/**
	 * Gets all of the attachments for a given case number. You can then
	 * search/filter the returned comments using any of the properties of a
	 * 'attachment' RESTful method:
	 * https://api.access.redhat.com/rs/cases/<caseNumber>/attachments.
	 * 
	 * @param caseNumber
	 *            A case number (e.g.00595293)
	 * @param startDate
	 *            Must be either: yyyy-MM-ddTHH:mm:ss or yyyy-MM-dd
	 * @param endDate
	 *            Must be either: yyyy-MM-ddTHH:mm:ss or yyyy-MM-dd
	 * @param kwargs
	 *            Additional properties to filter on. The RESTful interface can
	 *            only search on keywords; however, you can use this method to
	 *            post-filter the results returned. Simply supply a string array
	 *            of valid properties and their associated values.
	 * @return A list of comment objects
	 * @throws RequestException
	 *             An exception if there was a connection related issue.
	 * @throws MalformedURLException
	 */
	public List<Attachment> list(String caseNumber, String startDate,
			String endDate, String[] kwargs) throws RequestException,
			MalformedURLException {

		String url = "/rs/cases/{caseNumber}/attachments";
		url = url.replace("{caseNumber}", caseNumber);
		List<String> queryParams = new ArrayList<String>();
		if (startDate != null) {
			queryParams.add("startDate=" + startDate);
		}
		if (endDate != null) {
			queryParams.add("endDate=" + endDate);
		}
		String fullUrl = QueryBuilder.appendQuery(connectionManager.getConfig().getUrl()
						+ url, queryParams);
		com.redhat.gss.redhat_support_lib.parsers.Attachments attachments = get(connectionManager.getConnection(), fullUrl,
				com.redhat.gss.redhat_support_lib.parsers.Attachments.class);
		return (List<Attachment>) FilterHelper.filterResults(
				attachments.getAttachment(), kwargs);
	}

	/**
	 * Queries the API for the given attachment. RESTful method:
	 * https://api.access
	 * .redhat.com/rs/cases/<caseNumber>/attachments/<attachmentUUID>
	 * 
	 * @param caseNumber
	 *            The exact caseNumber you are interested in.
	 * @param attachmentUUID
	 *            The exact attachment UUID you're interested in.
	 * @param fileName
	 *            The name you want to give the file after it has been
	 *            downloaded. If none is specified, the original filename will
	 *            be used.
	 * @param destDir
	 *            The directory which the attachment should be saved in.
	 * @return The path to the saved file as a string.
	 * @throws Exception
	 *             An exception if there was a connection, file open, etc.
	 *             issue.
	 */
	public String get(String caseNumber, String attachmentUUID,
			String fileName, String destDir) throws Exception {

		String url = "/rs/cases/{caseNumber}/attachments/{attachmentUUID}";
		url = url.replace("{caseNumber}", caseNumber);
		url = url.replace("{attachmentUUID}", attachmentUUID);

		String fullUrl = connectionManager.getConfig().getUrl() + url;
		Response response = get(connectionManager.getConnection(), fullUrl,	Response.class);
		File tempfile = response.readEntity(File.class);
		String filePath = new String();
		if (destDir != null) {
			filePath = filePath.concat(destDir);
			if (!filePath.endsWith(File.separator)) {
				filePath = filePath.concat(File.separator);
			}
		}
		if (fileName != null) {
			filePath = filePath.concat(fileName);
		} else {
			String name = response.getStringHeaders().getFirst("Content-Disposition");
			String[] temp = name.split("\"");
			String decoded = MimeUtility.decodeWord(temp[1]);
			filePath = filePath.concat(decoded);
		}
		File file = new File(filePath);

		// moveFile will throw an exception here if it encounters issues
		FileUtils.moveFile(tempfile, file);

		return file.getName();
	}

	/**
	 * Removes the attachment from the case. RESTful method:
	 * https://api.access.redhat
	 * .com/rs/cases/<caseNumber>/attachments/<attachmentUUID>
	 * 
	 * @param caseNumber
	 *            The exact caseNumber you are interested in.
	 * @param attachmentUUID
	 *            The exact attachment UUID you're interested in.
	 * @return true if successful
	 * @throws RequestException
	 *             An exception if the attachment was not able to be removed.
	 * @throws MalformedURLException
	 */
	public boolean delete(String caseNumber, String attachmentUUID)
			throws RequestException, MalformedURLException {

		String url = "/rs/cases/{caseNumber}/attachments/{attachmentUUID}";
		url = url.replace("{caseNumber}", caseNumber);
		url = url.replace("{attachmentUUID}", attachmentUUID);

		String fullUrl = connectionManager.getConfig().getUrl() + url;
		return delete(connectionManager.getConnection(), fullUrl);
	}

	/**
	 * Add a new attachment
	 * 
	 * @param caseNumber
	 * @param publicVis
	 *            A public or private attachment
	 * @param fileName
	 *            Either a file in the CWD or a full path to a file.
	 * @param description
	 *            Description of the attachment
	 * @return URI of uploaded Attachment
	 * @throws Exception
	 *             An exception if there was a connection, file open, etc.
	 *             issue.
	 */
	public String add(String caseNumber, boolean publicVis, String fileName,
			String description) throws Exception {

		String url = "/rs/cases/{caseNumber}/attachments";
		url = url.replace("{caseNumber}", caseNumber);
		File file = new File(fileName);
		List<String> queryParams = new ArrayList<String>();
		queryParams.add("public=" + Boolean.toString(publicVis));
		String uri = null;
		// TODO: put in constants size is 2GB
		if (file.length() > connectionManager.getConfig().getFtpFileSize()) {
			FTPClient ftp = null;
			try {
				ftp = connectionManager.getFTP();
				ftp.cwd(connectionManager.getConfig().getFtpDir());
				ftp.enterLocalPassiveMode();
				if (!ftp.storeFile(file.getName(), new FileInputStream(file))) {
					throw new FTPException("Error during FTP store file.");
				}

			} finally {
				ftp.logout();
			}

			String cmntText = "The file "
					+ fileName
					+ " exceeds the byte limit to attach a file to a case; therefore, the file was uploaded to "
					+ connectionManager.getConfig().getFtpHost() + "as "
					+ file.getName();
			Comment comment = new Comment();
			comment.setCaseNumber(caseNumber);
			comment.setPublic(true);
			comment.setText(cmntText);
			Comments comments = new Comments(connectionManager);
			comments.add(comment);
		} else {
			String fullUrl = connectionManager
					.getConfig().getUrl() + url;
			uri = upload(connectionManager.getConnection(), fullUrl, file, description).getStringHeaders().getFirst("location");
		}
		return uri;
	}
}
