package co.wakarimasen.chanexplorer.imageboard;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.SparseArray;

import com.mindprod.boyer.Boyer;

public class Parser {

	private final static Pattern post_omitted = Pattern.compile("([0-9]+) post[s]{0,1} omitted");
	private final static Pattern post_image_omitted = Pattern.compile("([0-9]+) post[s]{0,1} and ([0-9]+) image repl");
	private final static Pattern sz_match = Pattern.compile("([0-9]+)x([0-9]+)");
	private final static Pattern iden_match = Pattern.compile("<img src=\"([^\"]+)\" alt=\"[^\"]+\" title=\"[^\"]+\" class=\"identityIcon\"");
	private final static Pattern quote_match = Pattern.compile("ass=\"quotelink\">&gt;&gt;([0-9]+)");

	public static Post[] parse(String boardHtml, boolean threaded, Set<Integer> ignored, int threadReplies, int greenTextColor) throws ChanParserException, BannedException, NotFoundException{
		List<Post> posts = new ArrayList<Post>();
		SparseArray<Post> r_posts = new SparseArray<Post>();
		Boyer boardBoyerHtml = new Boyer(boardHtml);
		int parserPos = boardBoyerHtml.indexOf("<div class=\"board\">") + 19;
		int threadPos;
		int postPos;
		int finalPost;
		int nextThreadPos;
		int threadId;
		int replies;
		boolean isBoard = boardBoyerHtml.indexOf("<div class=\"postingMode desktop\">Posting mode: Reply</div>") == -1;
		if (boardBoyerHtml.indexOf("<title>4chan - 404 Not Found</title>") != -1) {
			throw new NotFoundException();
		}
		if (boardBoyerHtml.indexOf("<title>4chan - Banned</title>") != -1) {
			throw new BannedException();
		}
		while ((threadPos = boardBoyerHtml.indexOf("<div class=\"thread\"", parserPos)) != -1) {
			// Get the thread id
			threadId = parseInt(getBetween("id=\"t", "\">", boardHtml, boardBoyerHtml, threadPos));
			replies = 0;
			nextThreadPos = boardBoyerHtml.indexOf("<div class=\"thread\"", threadPos + 19);
			if (nextThreadPos == -1) {
				nextThreadPos = boardHtml.length();
			}
			postPos = threadPos;
			finalPost = threadPos + 19;
			while ((postPos = boardBoyerHtml.indexOf("<div class=\"postContainer", postPos)) != -1 && postPos < nextThreadPos) {
				Post post = new Post(greenTextColor);
				post.setThreadId(threadId);
				post.setId(parseInt(getBetween("id=\"pc", "\"", boardHtml, boardBoyerHtml, postPos)));
				String namesubject = getBetween("<span class=\"name\">", "<span class=\"subject\">", boardHtml, boardBoyerHtml, postPos);
				if (post.isThread()) {
					if (getBetween("</blockquote>", "<hr>", boardHtml, boardBoyerHtml, postPos).indexOf("class=\"summary desktop\"") != -1) {
						String oms = getBetween("<span class=\"summary desktop\">", "</span>", boardHtml, boardBoyerHtml, postPos);
						Matcher m1 = post_image_omitted.matcher(oms);
						if (m1.find()) {
							post.setOmitted(parseInt(m1.group(1)), parseInt(m1.group(2)));
						} else {
							Matcher m2 = post_omitted.matcher(oms);
							if (m2.find()) {
								post.setOmitted(parseInt(m2.group(1)), 0);
							}
						}
					}
				}
				// isAdmin
				if (getBetween("<span class=\"nameBlock", "<span class=\"name\">", boardHtml, boardBoyerHtml, postPos).indexOf("capcodeAdmin") != -1) {
					post.setAdmin(true);
				}
				// isMod
				if (getBetween("<span class=\"name\">", "</span>", boardHtml, boardBoyerHtml, postPos).indexOf("\"color:#800080\"") != -1) {
					post.setMod(true);
				}
				// isLocked
				if (namesubject.indexOf("title=\"Closed\"") != -1) {
					post.setLocked(true);
				}
				// isSticky
				if (namesubject.indexOf("title=\"Sticky\"") != -1) {
					post.setSticky(true);
				}
				// idenIcon
				post.setIdenIcon(null);
				if (namesubject.indexOf("\"identityIcon\"") != -1) {
					String rel = getBetween("<span class=\"name\">", "<span class=\"subject", boardHtml, boardBoyerHtml, postPos);
					Matcher m1 = iden_match.matcher(rel);
					if (m1.find()) {
						post.setIdenIcon(m1.group(1));
					}
				}
				// Email
				if (getBetween("<span class=\"subject\">", "<blockquote", boardHtml, boardBoyerHtml, postPos).indexOf("mailto:") != -1) {
					try {
						post.setEmail(URLDecoder.decode(getBetween("href=\"mailto:", "\"", boardHtml, boardBoyerHtml, postPos), "UTF-8"));
					} catch (UnsupportedEncodingException e) {
						post.setEmail(getBetween("href=\"mailto:", "\"", boardHtml, boardBoyerHtml, postPos));
					}
				} else {
					post.setEmail(null);
				}
				// Name
				if (post.isMod()) {
					post.setName((getBetween("style=\"color:#800080\">", "</span>", boardHtml, boardBoyerHtml, postPos)));
				} else {
					post.setName((getBetween("<span class=\"name\">", "</span>", boardHtml, boardBoyerHtml, postPos)));
				}
				// Trip
				post.setTripcode(null);
				if (namesubject.indexOf("postertrip") != -1) {
					post.setTripcode((getBetween("<span class=\"postertrip\">", "</span>", boardHtml, boardBoyerHtml, postPos)));
				}
				// Id
				if (namesubject.indexOf("posteruid") != -1) {
					post.setPosterId((getBetween("posts by this ID\">", "</span>", boardHtml, boardBoyerHtml, postPos)));
				} else {
					post.setPosterId(null);
				}
				// sp/Flag
				if (namesubject.indexOf("countryFlag") != -1) {
					String flg = (getBetween("<img src=\"", "class=\"countryFlag\"", boardHtml, boardBoyerHtml, postPos));
					post.setFlag(new String(flg.substring(0, flg.indexOf('"'))));
				} else {
					post.setFlag(null);
				}
				// Subject
				post.setSubject((getBetween("<span class=\"subject\">", "</span>", boardHtml, boardBoyerHtml, boardBoyerHtml.indexOf("postInfo desktop", postPos))));
				// Date
				post.setTimestamp(parseLong(getBetween("data-utc=\"", "\"", boardHtml, boardBoyerHtml, postPos)));
				// Comment
				post.setComment((getBetween("<blockquote class=\"postMessage\" id=\"m"+ post.getId() +"\">", "</blockquote>", boardHtml, boardBoyerHtml, postPos)));

				// File
				if (getBetween("<span class=\"nameBlock", "</blockquote>", boardHtml, boardBoyerHtml, postPos).indexOf("class=\"file\"") != -1) {
					post.setFile(true);
					String fileInfo = getBetween("<div class=\"fileText\"", "</div>", boardHtml, boardBoyerHtml, postPos);
					if (fileInfo.length() == 0) {
						post.setFileDeleted(true);
					} else {
						post.setFileDeleted(false);
						post.setImage((getBetween("File: <a href=\"", "\"", boardHtml, boardBoyerHtml, postPos)));
						if (fileInfo.indexOf("<span title=") == -1) {
						   // post.setFilename("title");
						    post.setFilename((getBetween("<span>", "</span>", boardHtml, boardBoyerHtml, boardBoyerHtml.indexOf("<div class=\"file", postPos))));
						} else {
						//	post.setFilename("title");
						    post.setFilename((getBetween("<span title=\"", "\"", boardHtml, boardBoyerHtml, boardBoyerHtml.indexOf("<div class=\"file", postPos))));
						}
						post.setSpoiler((fileInfo.indexOf("Spoiler Image") != -1));
						post.setFilesize(getBetween("</a>-(", "\">File:", fileInfo, 1));
						post.setThumbnail((getBetween("<img src=\"", "\"", boardHtml, boardBoyerHtml, boardBoyerHtml.indexOf("<div class=\"file", postPos))));
						//post.setThHeight(2);
						post.setThHeight(parseInt(getBetween("height: ", "px", boardHtml, boardBoyerHtml, boardBoyerHtml.indexOf("<div class=\"file", postPos))));
						//post.setThWidth(3);
						post.setThWidth(parseInt(getBetween("width: ", "px", boardHtml, boardBoyerHtml, boardBoyerHtml.indexOf("<div class=\"file", postPos))));
						Matcher m1 = sz_match.matcher(fileInfo);
						if (m1.find()) {
							post.setWidth(parseInt(m1.group(1)));
							post.setHeight(parseInt(m1.group(2)));
						}
					}
				}
				if ((ignored == null || !ignored.contains(post.getThreadId())) &&
						(!isBoard || threadReplies == 0 || replies < threadReplies)) {
					posts.add(post);
					r_posts.put(post.getId(), post);
					replies++;
				}
				finalPost = postPos = boardBoyerHtml.indexOf("</blockquote>", postPos) + 13;

			}
			parserPos = finalPost;
		}
		if (posts.size() == 0) {
			throw new ChanParserException("No posts were found.");
		}
		//for (int i=posts.size()-1; i>=0; i--) {
		for (int i=0; i<posts.size(); i++) {
			Matcher m1 = quote_match.matcher(posts.get(i).getComment());
			while (m1.find()) {
				int id = Integer.parseInt(m1.group(1));
				Post p = r_posts.get(id);
				if (p != null && (p.getReplies() == null || !p.getReplies().contains(p.getId()))) {
					p.addReply(posts.get(i).getId());
				}
			}
		}
		Post[] final_posts = new Post[posts.size()];
		if (threaded) {
			for (int i=0; i<final_posts.length; i++) {
				Post p = posts.get(i);
				final_posts[i] = p;
				if (p.hasReplies()) {;
					for (int j=0; j<p.getReplies().size(); j++) {
						Post r =  r_posts.get(p.getReplies().get(j));
						if (r != null) {
							final_posts[++i] = r;
							r_posts.delete(p.getReplies().get(j));
						}
					}
				}
			}
		} else {
			posts.toArray(final_posts);
		}
		return final_posts;
	}

	private static final int parseInt(String str) throws ChanParserException {
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			throw new ChanParserException("Tried to parse "+str+" into integer.");
		}
	}

	private static final long parseLong(String str) throws ChanParserException {
		try {
			return Long.parseLong(str);
		} catch (NumberFormatException e) {
			throw new ChanParserException("Tried to parse "+str+" into Long.");
		}
	}

	private static final String getBetween(String start, String end, String haystack, int start_from) throws ChanParserException {
		try {
			return new String(haystack.substring((start_from = haystack.indexOf(start, start_from)+start.length()), haystack.indexOf(end, start_from)));
		} catch (StringIndexOutOfBoundsException e) {
			throw new ChanParserException("String index out of bounds. Haystack Length: "+haystack.length());
		}
	}

	private static final String getBetween(String start, String end, String haystack, Boyer boyer, int start_from) throws ChanParserException {
		try {
			return new String(haystack.substring((start_from = boyer.indexOf(start, start_from)+start.length()), boyer.indexOf(end, start_from)));
		} catch (StringIndexOutOfBoundsException e) {
			throw new ChanParserException("String index out of bounds. Haystack Length: "+haystack.length());
		}
	}

	public static class ChanParserException extends Exception {
		private static final long serialVersionUID = 1667660700840058145L;
		public ChanParserException(String message) {
			super(message);
		}
	}

	public static class BannedException extends Exception {
		private static final long serialVersionUID = 1667660700840058146L;
	}

	public static class NotFoundException extends Exception {
		private static final long serialVersionUID = 1667660700840058147L;
	}
}
