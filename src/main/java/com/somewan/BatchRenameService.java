package com.somewan;

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
public class BatchRenameService {
    private static final Logger LOG = LogManager.getLogger(BatchRenameService.class);
    private static final String SUFFIX = ".html";
    private static final String UNTITLE = "无标题";

    private int count = 0;
    private int updateCount = 0;
    private int unUpdateCount = 0;

    private static final String AUTH_TOKEN = "S=s50:U=99f2ca:E=16127f24773:C=159d04117e0:P=1cd:A=en-devtoken:V=2:H=eaca86adc3ca3c73286cc6abd78756b7";// yinxiang
    //private static final String AUTH_TOKEN = "S=s1:U=934d7:E=1612814d623:C=159d063a788:P=1cd:A=en-devtoken:V=2:H=f1070a8b785acf68eec552499344e43f";// sandbox
    private UserStoreClient userStore;
    private NoteStoreClient noteStore;

    /**
     * Intialize UserStore and NoteStore clients. During this step, we
     * authenticate with the Evernote web service. All of this code is boilerplate
     * - you can copy it straight into your application.
     */
    public BatchRenameService(String token) throws EDAMUserException, EDAMSystemException, TException, TTransportException, InterruptedException {
        // Set up the UserStore client and check that we can speak to the server
        EvernoteAuth evernoteAuth = new EvernoteAuth(EvernoteService.YINXIANG, token);// 这里在沙盒环境要改为EvernoteService.Sandbox!!
        //EvernoteAuth evernoteAuth = new EvernoteAuth(EvernoteService.SANDBOX, token);
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
        try {
            noteStore = factory.createNoteStoreClient();
        } catch (EDAMSystemException e) {
            if(e.getErrorCode() == EDAMErrorCode.RATE_LIMIT_REACHED) {
                // 重试
                int waitTime = e.getRateLimitDuration();// s
                LOG.warn("正在等待重试。等待时间：{}s", waitTime);
                Thread.sleep((long)waitTime * 1000);
                noteStore = factory.createNoteStoreClient();//TODO 这里只重试了一次，如果再次失败，这里是有问题的。
            } else {
                throw e;
            }
        }
    }

    public void batchRename() throws Exception{
        LOG.info("开始批量重命名");

        List<Notebook> notebooks;
        // 获取笔记本列表
        try {
            notebooks = noteStore.listNotebooks();
        } catch (EDAMSystemException e) {
            if(e.getErrorCode() == EDAMErrorCode.RATE_LIMIT_REACHED) {
                // 重试
                int waitTime = e.getRateLimitDuration();// s
                LOG.warn("正在等待重试。等待时间：{}s", waitTime);
                Thread.sleep((long)waitTime * 1000);
                notebooks = noteStore.listNotebooks();
            } else {
                throw e;
            }
        }

        // 官方文档有说明，返回的notebooks不可能为null
        LOG.info("获取到的笔记本列表数量：{} ", notebooks.size());

        // 获取每个笔记，重命名
        for(Notebook notebook: notebooks) {
            renameOneNotebook(notebook);
        }
        LOG.info("共{}条笔记；重命名了{}条笔记；未重命名{}条笔记", updateCount + unUpdateCount, updateCount, unUpdateCount);
        LOG.info("重命名结束");
        LOG.info("====================================");
    }

    /**
     * 重命名某一笔记本中的笔记
     * @param notebook
     * @throws Exception
     */
    private void renameOneNotebook(Notebook notebook) throws Exception {
        LOG.info("笔记本名称：{}", notebook.getName());

        // 获取metadata
        // 创建filter
        NoteFilter filter =  new NoteFilter();
        filter.setWords("intitle:html");
        filter.setNotebookGuid(notebook.getGuid());
        filter.setAscending(true);
        // 创建resultspc
        NotesMetadataResultSpec resultSpec = new NotesMetadataResultSpec();
        //resultSpec.setIncludeTitle(true);
        // 获取笔记元数据
        NotesMetadataList notesMetadataList;
        try {
            notesMetadataList = noteStore.findNotesMetadata(filter, 0, 1000, resultSpec);//? 有没有什么办法可以获取到笔记本中笔记的数量
        } catch (EDAMSystemException e) {
            if(e.getErrorCode() == EDAMErrorCode.RATE_LIMIT_REACHED) {
                // 重试
                int waitTime = e.getRateLimitDuration();// s
                LOG.warn("正在等待重试。等待时间：{}s", waitTime);
                Thread.sleep((long)waitTime * 1000);
                notesMetadataList = noteStore.findNotesMetadata(filter,0,1000,resultSpec);//? 有没有什么办法可以获取到笔记本中笔记的数量
            } else {
                throw e;
            }
        }
        LOG.info("笔记本中共有{}条笔记", notesMetadataList.getNotes().size());

        //根据metadata（guid），获取笔记
        for(NoteMetadata noteMetadata: notesMetadataList.getNotes()) {
            renameOneNote(noteMetadata);
        }
    }

    /**
     * 重命名某一笔记
     * @param noteMetadata
     * @throws Exception
     */
    private void renameOneNote(NoteMetadata noteMetadata) throws Exception {
        try {
            count++;
            Note note;
            try {
                note = noteStore.getNote(noteMetadata.getGuid(), false, false, false, false);
            } catch (EDAMSystemException e) {
                if(e.getErrorCode() == EDAMErrorCode.RATE_LIMIT_REACHED) {
                    // 重试
                    int waitTime = e.getRateLimitDuration();// s
                    LOG.warn("正在等待重试。等待时间：{}s", waitTime);
                    Thread.sleep((long)waitTime * 1000);
                    note = noteStore.getNote(noteMetadata.getGuid(), false, false, false, false);
                } else {
                    throw e;
                }
            }
            LOG.info("正在处理第({})条笔记：({})", count, note.getTitle());
            //修改笔记标题
            boolean update = renameNote(note);
            if (update) {
                try {
                    noteStore.updateNote(note);
                } catch (EDAMSystemException e) {
                    if(e.getErrorCode() == EDAMErrorCode.RATE_LIMIT_REACHED) {
                        // 重试
                        int waitTime = e.getRateLimitDuration();// s
                        LOG.warn("正在等待重试。等待时间：{}s", waitTime);
                        Thread.sleep((long)waitTime * 1000);
                        noteStore.updateNote(note);
                    } else {
                        throw e;
                    }
                }
                updateCount++;
            } else {
                unUpdateCount++;
            }
        } catch (EDAMSystemException e) {
            if(e.getErrorCode() == EDAMErrorCode.RATE_LIMIT_REACHED) {
                // 重试
                renameOneNote(noteMetadata);
            } else {
                throw e;
            }
        }
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
                LOG.info("笔记：标题未修改", note.getTitle());
                return false;
            case 0:
                note.setTitle(UNTITLE);
                LOG.info("笔记：标题已修改为({})", note.getTitle());
                return true;
            default:
                note.setTitle(title.substring(0, index).trim());
                LOG.info("笔记：标题已修改为({})", note.getTitle());
                return true;
        }
    }

    public static void main(String[] args) {
        BatchRenameService batchRenameService;
        try {
            batchRenameService = new BatchRenameService(AUTH_TOKEN);
            batchRenameService.batchRename();
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
                        + " parameter: " + e.getParameter() + e);
            }
        } catch (EDAMSystemException e) {
            LOG.error("System error: " + e.getErrorCode().toString() + e.getRateLimitDuration() + "s后再试。");
        } catch (TTransportException t) {
            LOG.error("Networking error: " + t.getMessage());
        } catch (Exception e) {
            LOG.error("Error：{}", e);
        }
    }

}
