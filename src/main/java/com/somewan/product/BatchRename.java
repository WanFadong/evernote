package com.somewan.product;

import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteMetadata;
import com.evernote.edam.notestore.NotesMetadataList;
import com.evernote.edam.notestore.NotesMetadataResultSpec;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.thrift.TException;
import com.evernote.thrift.transport.TTransportException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Created by wan on 2017/1/23.
 */
public class BatchRename {
    private static final Logger LOG = LogManager.getLogger(BatchRename.class);
    private static final String SUFFIX = ".html";
    private static final String UNTITLE = "无标题";

    private static final String AUTH_TOKEN = "S=s1:U=934d7:E=16120bd6219:C=159c90c32e8:P=1cd:A=en-devtoken:V=2:H=7122c7783d7d70457e0a11b24eafbbda";
    private UserStoreClient userStore;
    private NoteStoreClient noteStore;

    /**
     * Intialize UserStore and NoteStore clients. During this step, we
     * authenticate with the Evernote web service. All of this code is boilerplate
     * - you can copy it straight into your application.
     */
    public BatchRename(String token) throws EDAMUserException, EDAMSystemException, TException, TTransportException{
        // Set up the UserStore client and check that we can speak to the server
        EvernoteAuth evernoteAuth = new EvernoteAuth(EvernoteService.SANDBOX, token);//TODO 沙盒的Auth
        ClientFactory factory = new ClientFactory(evernoteAuth);
        userStore = factory.createUserStoreClient();

        boolean versionOk = userStore.checkVersion("Evernote EDAMDemo (Java)",
                com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR,
                com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR);
        if (!versionOk) {
            LOG.error("Incompatible Evernote client protocol version");
            System.exit(1);
        }

        // Set up the NoteStore client
        noteStore = factory.createNoteStoreClient();
    }

    public void batchRename() throws Exception{
        LOG.info("开始批量重命名");

        // 获取笔记本列表
        List<Notebook> notebooks = noteStore.listNotebooks();
        // 官方文档有说明，返回的notebooks不可能为null
        LOG.info("获取到的笔记本列表数量：{} ", notebooks.size());

        // 获取每个笔记，重命名
        int updateCount = 0;
        int unUpdateCount = 0;
        for(Notebook notebook: notebooks) {
            LOG.info("笔记本名称：{}", notebook.getName());

            // 获取metadata
            // 创建filter
            NoteFilter filter =  new NoteFilter();
            filter.setNotebookGuid(notebook.getGuid());
            filter.setAscending(true);
            // 创建resultspc
            NotesMetadataResultSpec resultSpec = new NotesMetadataResultSpec();
            //resultSpec.setIncludeTitle(true);
            // 获取笔记元数据
            NotesMetadataList notesMetadataList = noteStore.findNotesMetadata(filter,0,1000,resultSpec);//? 有没有什么办法可以获取到笔记本中笔记的数量
            LOG.info("笔记本中共有{}条笔记", notesMetadataList.getNotes().size());

            //根据metadata（guid），获取笔记
            for(NoteMetadata noteMetadata: notesMetadataList.getNotes()) {
                Note note = noteStore.getNote(noteMetadata.getGuid(),false,false,false, false);
                //修改笔记标题
                boolean update = renameNote(note);
                if(update) {
                    noteStore.updateNote(note);
                    updateCount++;
                } else {
                    unUpdateCount++;
                }
            }
        }
        LOG.info("共{}条笔记；重命名了{}条笔记；未重命名{}条笔记", updateCount + unUpdateCount, updateCount, unUpdateCount);
        LOG.info("重命名结束");
    }

    /**
     * 判断名称中是否含有".html"，如果有，重命名笔记。
     * @param note
     * @return 表示是否重命名了
     */
    private boolean renameNote(Note note) {
        String title = note.getTitle();
        if(title.endsWith(SUFFIX)) {
            note.setTitle(title.substring(0, title.indexOf(SUFFIX)));
        }
        int index = title.indexOf(SUFFIX);
        switch(index) {
            case -1:
                LOG.info("笔记：{} 标题未修改", note.getTitle());
                return false;
            case 0:
                note.setTitle(UNTITLE);
                LOG.info("笔记：{} 标题已修改", note.getTitle());
                return true;
            default:
                note.setTitle(title.substring(0, index));
                LOG.info("笔记：{} 标题已修改", note.getTitle());
                return true;
        }
    }

    public static void main(String[] args) {
        BatchRename batchRename;
        try {
            batchRename = new BatchRename(AUTH_TOKEN);
        } catch(Exception e) {
            LOG.error("初始化BatchRename失败",e);
            return;
        }

        try {
            batchRename.batchRename();
            LOG.info("批量重命名成功");
        } catch (EDAMUserException e) {
            // These are the most common error types that you'll need to
            // handle
            // EDAMUserException is thrown when an API call fails because a
            // paramter was invalid.
            if (e.getErrorCode() == EDAMErrorCode.AUTH_EXPIRED) {
                LOG.error("Your authentication token is expired!");
            } else if (e.getErrorCode() == EDAMErrorCode.INVALID_AUTH) {
                LOG.error("Your authentication token is invalid!");
            } else if (e.getErrorCode() == EDAMErrorCode.QUOTA_REACHED) {
                LOG.error("Your authentication token is invalid!");
            } else {
                LOG.error("Error: " + e.getErrorCode().toString()
                        + " parameter: " + e.getParameter());
            }
        } catch (EDAMSystemException e) {
            LOG.error("System error: " + e.getErrorCode().toString());
        } catch (TTransportException t) {
            LOG.error("Networking error: " + t.getMessage());
        } catch (Exception e) {
            LOG.error("Error：{}", e);
        }
    }

}
