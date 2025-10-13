package com.chatbot.model;

import jakarta.persistence.*;

@Entity
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String fileName;


    private String fileType;
   
    @Lob
    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "dropbox_link")
    private String dropboxLink;

    public String getDropboxLink() {
        return dropboxLink;
    }

    public void setDropboxLink(String dropboxLink) {
        this.dropboxLink = dropboxLink;
    }


    @Lob
    private byte[] data;

    // Optional: Link to a user
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public UploadedFile() {}

    public UploadedFile(String fileName, String fileType, byte[] data, User user) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.data = data;
        this.user = user;
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileType() {
		return fileType;
	}

	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	public UploadedFile(Long id, String fileName, String fileType, byte[] data, User user) {
		super();
		this.id = id;
		this.fileName = fileName;
		this.fileType = fileType;
		this.data = data;
		this.user = user;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}
	

	public String getTextContent() {
	    return textContent;
	}

	public void setTextContent(String textContent) {
	    this.textContent = textContent;
	}


}
