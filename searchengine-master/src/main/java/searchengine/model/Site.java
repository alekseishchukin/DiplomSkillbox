package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "site")
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')")
    private IndexingStatus status;
    @Column(name = "status_time", columnDefinition = "DATETIME", nullable = false)
    private String statusTime;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL)
    private List<Page> pages;
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL)
    private List<Lemma> lemmas;
}