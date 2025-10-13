package com.chatbot.model;

import jakarta.persistence.*;

@Entity
@Table(name = "RESOURCE")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String name;

    @Column(name="DRIVE_LINK", nullable=false, length=1000)
    private String driveLink;

    @Column(name="TYPE")
    private String type = "Academic";

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDriveLink() { return driveLink; }
    public void setDriveLink(String driveLink) { this.driveLink = driveLink; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
