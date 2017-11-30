package au.org.garvan.kccg.annotations.pipeline.engine.dbhandlers;

import au.org.garvan.kccg.annotations.pipeline.engine.entities.lexical.APGene;
import au.org.garvan.kccg.annotations.pipeline.engine.entities.lexical.LexicalEntity;
import au.org.garvan.kccg.annotations.pipeline.engine.entities.linguistic.APSentence;
import au.org.garvan.kccg.annotations.pipeline.engine.entities.linguistic.APToken;
import au.org.garvan.kccg.annotations.pipeline.engine.entities.publicational.Article;
import au.org.garvan.kccg.annotations.pipeline.engine.entities.publicational.Author;
import au.org.garvan.kccg.annotations.pipeline.engine.entities.publicational.Publication;
import au.org.garvan.kccg.annotations.pipeline.engine.enums.SearchQueryParams;
import au.org.garvan.kccg.annotations.pipeline.engine.utilities.Pair;
import com.google.common.base.Strings;
import iot.jcypher.database.DBAccessFactory;
import iot.jcypher.database.DBProperties;
import iot.jcypher.database.DBType;
import iot.jcypher.database.IDBAccess;
import iot.jcypher.query.JcQuery;
import iot.jcypher.query.JcQueryResult;
import iot.jcypher.query.LiteralMap;
import iot.jcypher.query.api.IClause;
import iot.jcypher.query.factories.clause.*;
import iot.jcypher.query.values.JcCollection;
import iot.jcypher.query.values.JcNode;
import iot.jcypher.query.values.JcRelation;
import iot.jcypher.query.values.JcString;
import iot.jcypher.query.writer.Format;
import iot.jcypher.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by ahmed on 30/10/17.
 */
public class GraphDBHandlerDepricated {

    private final Logger slf4jLogger = LoggerFactory.getLogger(GraphDBHandlerDepricated.class);

    private static final boolean ENABLE_PRINTING = false;

    Properties props = new Properties();
    IDBAccess remote;

    public GraphDBHandlerDepricated() {

        props.setProperty(DBProperties.SERVER_ROOT_URI, "http://localhost:7474");

        remote = DBAccessFactory.createDBAccess(DBType.REMOTE, props);

    }

    /**
     * map to CYPHER statements and map to JSON, print the mapping results to System.out
     *
     * @param query
     * @param title
     * @param format
     */
    private static void print(JcQuery query, String title, Format format) {
        System.out.println("QUERY: " + title + " --------------------");
        // map to Cypher
        String cypher = Util.toCypher(query, format);
        System.out.println("CYPHER --------------------");
        System.out.println(cypher);

        // map to JSON
        String json = Util.toJSON(query, format);
        System.out.println("");
        System.out.println("JSON   --------------------");
        System.out.println(json);

        System.out.println("");
    }

    private static void print(JcQueryResult queryResult, String title) {
        System.out.println("RESULT OF QUERY: " + title + " --------------------");
        String resultString = Util.writePretty(queryResult.getJsonResult());
        System.out.println(resultString);
    }

    public void createArticleQuery(Article article) {

        List<IClause> queryClauses = new ArrayList<>();

        //Create Article node and creation clause
        JcNode nodeArticle = new JcNode("nodeArticle");
        IClause articleClause = MERGE.node(nodeArticle).label("Article")
                .property("PMID").value(Integer.toString(article.getPubMedID()))
                .property("Language").value(article.getLanguage());


        //Create authors nodes and their respective clauses (This includes both creation and mapping clauses)
        List<JcNode> nodeListAuthors = new ArrayList<>();
        List<IClause> authorsClauses = new ArrayList<>();

        for (int x = 0; x < article.getAuthors().size(); x++) {
            JcNode tempAuthor = new JcNode("nodeAuthor" + Integer.toString(x));
            nodeListAuthors.add(tempAuthor);
            authorsClauses.add(MERGE.node(tempAuthor).label("Author")
                    .property("Initials").value(article.getAuthors().get(x).getInitials())
                    .property("ForeName").value(article.getAuthors().get(x).getForeName())
                    .property("LastName").value(article.getAuthors().get(x).getLastName()));
            authorsClauses.add(CREATE.node(tempAuthor).relation().out()
                    .type("WROTE").property("Order").value(x + 1)
                    .node(nodeArticle));
        }

        //Create publicational node and creation clause
        JcNode nodePublication = new JcNode("nodePublication");
        IClause publicationClause = MERGE.node(nodePublication).label("Publication")
                .property("Title").value(article.getPublication().getTitle())
                .property("IsoAbbreviation").value(article.getPublication().getIsoAbbreviation())
                .property("IssnType").value(article.getPublication().getIssnType())
                .property("IssnNumber").value(article.getPublication().getIssnNumber());
        IClause publicationLinkClause = CREATE.node(nodeArticle).relation().out().type("PUBLISHED")
                .property("DatePublished")
                .value(article.getDatePublished())
                .node(nodePublication);

        queryClauses.add(articleClause);

        queryClauses.addAll(authorsClauses);
        queryClauses.add(publicationClause);
        queryClauses.add(publicationLinkClause);

        fillEntitiesGraphData(queryClauses, article, nodeArticle);


        JcQuery query = new JcQuery();
        query.setClauses(getClausesArray(queryClauses));


        JcQueryResult result = remote.execute(query);


        if (result.getDBErrors().size() > 0)
            slf4jLogger.info(String.format("Graph DB Insertion Done. DB Error:%s ", result.getDBErrors().toString()));
        else
            slf4jLogger.info(String.format("Graph DB Insertion done without errors."));


    }

    private void fillEntitiesGraphData(List<IClause> queryClauses, Article article, JcNode nodeArticle) {
        //TODO: Find entities from Abstract and fill node and relationship
        Map<APSentence, List<APToken>> entities = article.getArticleAbstract().getTokensWithEntities();
        if (entities.size() > 0) {
            for (Map.Entry<APSentence, List<APToken>> entry : entities.entrySet()) {
                APSentence sent = entry.getKey();
                List<APToken> tokens = entry.getValue();
                for (APToken token : tokens) {
                    for (LexicalEntity lex : token.getLexicalEntityList()) {
                        if (lex instanceof APGene) {
                            APGene gene = (APGene) lex;
                            JcNode nodeGene = new JcNode(String.format("nodeGene%d_%d", sent.getId(), token.getId()));
                            IClause geneClause = MERGE.node(nodeGene).label("Gene").label("Entity")
                                    .property("HGNCID").value(gene.getHGNCID())
                                    .property("Symbol").value(gene.getApprovedSymbol());
                            IClause geneLinkClause =
                                    CREATE.node(nodeArticle).relation().out().type("CONTAINS")
                                            .property("SentID").value(sent.getId())
                                            .property("DocOffsetBegin").value(sent.getDocOffset().getX() + token.getSentOffset().getX())
                                            .property("Field").value("Abstract")
                                            .node(nodeGene);
                            queryClauses.add(geneClause);
                            queryClauses.add(geneLinkClause);

                        } else {

                        }
                    }
                }

            }


        }

        //TODO: Find entities from Title if required
    }

    private IClause[] getClausesArray(List<IClause> input) {

        IClause[] arr = new IClause[input.size()];
        for (int x = 0; x < input.size(); x++)
            arr[x] = input.get(x);

        return arr;
    }
    /*

    The following section is for search operations.


     */

    public List<String> fetchArticles(Map<SearchQueryParams, Object> params) {
        Set<String> shortListedArticles = new HashSet<>();

        //Results storage for all params, and later will be sorted out.
        LinkedHashMap<SearchQueryParams, JcQueryResult> collectedResults = new LinkedHashMap<>();

        if (params.containsKey(SearchQueryParams.AUTHOR)) {
            Author author = (Author) params.get(SearchQueryParams.AUTHOR);
            runAuthorQuery(collectedResults, author, shortListedArticles);
        }

        if (params.containsKey(SearchQueryParams.PUBLICATION)) {
            Publication publication = (Publication) params.get(SearchQueryParams.PUBLICATION);
            runPublicationQuery(collectedResults, publication, shortListedArticles);
        }

        if (params.containsKey(SearchQueryParams.GENES)) {
            List<String> genes = (List<String>) params.get(SearchQueryParams.GENES);
            runGenesQuery(collectedResults, genes, shortListedArticles);
        }

        return new ArrayList<>(shortListedArticles);


}



    private JcQueryResult executeQueryClause(List<IClause> lstQueryClauses)
    {
        JcQuery query = new JcQuery();
        query.setClauses(getClausesArray(lstQueryClauses));
        if (ENABLE_PRINTING)
            print(query, "Search", Format.PRETTY_3);
        JcQueryResult result = remote.execute(query);
       if(ENABLE_PRINTING)
            print(result, "Result");
        return result;

    }

    private Pair<String, String> getPubIdentifier(Publication publication) {

        if (!Strings.isNullOrEmpty(publication.getIssnNumber())) {
            return Pair.of("IssnNumber", publication.getIssnNumber());
        }
        if (!Strings.isNullOrEmpty(publication.getIsoAbbreviation())) {
            return Pair.of("IsoAbbreviation", publication.getIsoAbbreviation());
        }
        if (!Strings.isNullOrEmpty(publication.getTitle())) {
            return Pair.of("Title", publication.getTitle());
        }

        return null;

    }




    private void runAuthorQuery (LinkedHashMap<SearchQueryParams, JcQueryResult> collectedResults, Author author, Set<String> shortListedArticles)
    {
        JcNode article = new JcNode("a");
        List<IClause> queryClauses = new ArrayList<>();

        JcNode auth = new JcNode("auth");
        JcRelation c = new JcRelation("c");
        JcString PMID = new JcString("PMID");
        queryClauses.add(MATCH.node(article).label("Article")
                .relation(c).in().type("WROTE")
                .node(auth).label("Author"));
        queryClauses.add(WHERE.valueOf(auth.property("ForeName")).EQUALS(author.getForeName())
                .AND().valueOf(auth.property("LastName")).EQUALS(author.getLastName())
                .AND().valueOf(auth.property("Initials")).EQUALS(author.getInitials()));
        queryClauses.add(RETURN.value(article.property("PMID")).AS(PMID));
        JcQueryResult result= executeQueryClause(queryClauses);
        shortListedArticles.addAll(result.resultOf(PMID));
        collectedResults.put(SearchQueryParams.AUTHOR, result);


    }

    private void runPublicationQuery (LinkedHashMap<SearchQueryParams, JcQueryResult> collectedResults,Publication publication, Set<String> shortListedArticles) {

        Pair<String, String> pubIdentifier = getPubIdentifier(publication);
        if (pubIdentifier != null) {
            JcNode article = new JcNode("a");
            List<IClause> queryClauses = new ArrayList<>();

            JcNode pub = new JcNode("p");
            JcRelation c = new JcRelation("c");
            JcString PMID = new JcString("PMID");
            queryClauses.add(MATCH.node(article).label("Article")
                    .relation(c).out().type("PUBLISHED")
                    .node(pub).label("Publication"));

            if (shortListedArticles.size() > 0) {
                queryClauses.add(WHERE.valueOf(pub.property(pubIdentifier.getFirst())).EQUALS(pubIdentifier.getSecond())
                        .AND().valueOf(article.property("PMID")).IN(new JcCollection(new ArrayList<>(shortListedArticles))));

            } else {
                queryClauses.add(WHERE.valueOf(pub.property(pubIdentifier.getFirst())).EQUALS(pubIdentifier.getSecond()));

            }
            queryClauses.add(RETURN.value(article.property("PMID")).AS(PMID));

            JcQueryResult result= executeQueryClause(queryClauses);
            shortListedArticles.addAll(result.resultOf(PMID));
            collectedResults.put(SearchQueryParams.PUBLICATION, result);


        }
    }


    private void runGenesQuery(LinkedHashMap<SearchQueryParams, JcQueryResult> collectedResults, List<String> genes, Set<String> shortListedArticles) {
        JcNode article = new JcNode("a");
        List<IClause> queryClauses = new ArrayList<>();

        switch (genes.size()) {
            case 1:
                JcNode gene1 = new JcNode("g1");
                JcRelation c = new JcRelation("C");
                JcString PMID = new JcString("PMID");
                JcString docOffsetBegin = new JcString("docOffsetBegin");
                queryClauses.add(MATCH.node(article).label("Article")
                        .relation(c).out().type("CONTAINS")
                        .node(gene1).label("Gene"));

                if (shortListedArticles.size() > 0) {
                    queryClauses.add(WHERE.valueOf(gene1.property("Symbol")).EQUALS(genes.get(0))
                            .AND().valueOf(article.property("PMID")).IN(new JcCollection(new ArrayList<>(shortListedArticles))));

                }
                else
                {
                    queryClauses.add(WHERE.valueOf(gene1.property("Symbol")).EQUALS(genes.get(0)));

                }

                queryClauses.add(RETURN.value(article.property("PMID")).AS(PMID));
                queryClauses.add(RETURN.value(c.property("DocOffsetBegin")).AS(docOffsetBegin));

                JcQueryResult result1= executeQueryClause(queryClauses);

                List<LiteralMap> rMap =  result1.resultMapListOf(PMID,docOffsetBegin);

                collectedResults.put(SearchQueryParams.GENES,result1);


                break;
            case 2:

                JcNode gene21 = new JcNode("g1");
                JcNode gene22 = new JcNode("g2");
                JcRelation c21 = new JcRelation("C1");
                JcRelation c22 = new JcRelation("C2");
                JcString PMID2 = new JcString("PMID");
                JcString docOffsetBegin21 = new JcString("docOffsetBegin1");
                JcString docOffsetBegin22 = new JcString("docOffsetBegin2");

                queryClauses.add(MATCH.node(article).label("Article"));
                queryClauses.add(MATCH.node(article)
                        .relation(c21).out().type("CONTAINS")
                        .node(gene21).label("Gene"));
                queryClauses.add(MATCH.node(article)
                        .relation(c21).out().type("CONTAINS")
                        .node(gene22).label("Gene"));

                queryClauses.add(WHERE.valueOf(gene21.property("Symbol")).EQUALS(genes.get(0))
                        .AND().valueOf(gene22.property("Symbol")).EQUALS(genes.get(1)));
                queryClauses.add(RETURN.value(article.property("PMID")).AS(PMID2));
                queryClauses.add(RETURN.value(c21.property("DocOffsetBegin")).AS(docOffsetBegin21));
                queryClauses.add(RETURN.value(c22.property("DocOffsetBegin")).AS(docOffsetBegin22));

                JcQueryResult result2= executeQueryClause(queryClauses);


                collectedResults.put(SearchQueryParams.GENES,result2);

                break;
            case 3:
                JcNode gene31 = new JcNode("g1");
                JcNode gene32 = new JcNode("g2");
                JcNode gene33 = new JcNode("g3");
                JcRelation c31 = new JcRelation("C1");
                JcRelation c32 = new JcRelation("C2");
                JcRelation c33 = new JcRelation("C3");
                JcString PMID3 = new JcString("PMID");
                JcString docOffsetBegin31 = new JcString("docOffsetBegin1");
                JcString docOffsetBegin32 = new JcString("docOffsetBegin2");
                JcString docOffsetBegin33 = new JcString("docOffsetBegin3");

                queryClauses.add(MATCH.node(article).label("Article"));
                queryClauses.add(MATCH.node(article)
                        .relation(c31).out().type("CONTAINS")
                        .node(gene31).label("Gene"));
                queryClauses.add(MATCH.node(article)
                        .relation(c32).out().type("CONTAINS")
                        .node(gene32).label("Gene"));
                queryClauses.add(MATCH.node(article)
                        .relation(c33).out().type("CONTAINS")
                        .node(gene33).label("Gene"));

                queryClauses.add(WHERE.valueOf(gene31.property("Symbol")).EQUALS(genes.get(0))
                        .AND().valueOf(gene32.property("Symbol")).EQUALS(genes.get(1))
                        .AND().valueOf(gene33.property("Symbol")).EQUALS(genes.get(2)));
                queryClauses.add(RETURN.value(article.property("PMID")).AS(PMID3));
                queryClauses.add(RETURN.value(c31.property("DocOffsetBegin")).AS(docOffsetBegin31));
                queryClauses.add(RETURN.value(c32.property("DocOffsetBegin")).AS(docOffsetBegin32));
                queryClauses.add(RETURN.value(c33.property("DocOffsetBegin")).AS(docOffsetBegin33));

                JcQueryResult result3= executeQueryClause(queryClauses);
                collectedResults.put(SearchQueryParams.GENES,result3);

                break;
        }//Size




    }


}
