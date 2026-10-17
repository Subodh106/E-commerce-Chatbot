package com.example.demo.service;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import org.springframework.ai.document.Document;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatBotService {

    private final VectorStore vectorStore;

    private  final ChatClient chatClient;

    @Value("classpath:Knowledge/*.pdf")
    private Resource[] resourcesFiles;

    public ChatBotService(VectorStore vectorStore , ChatClient.Builder builder){
        this.vectorStore = vectorStore;
        this.chatClient = builder.build();
    }


    @PostConstruct
    public void loadKnowledgeBase(){
        // Read all files
        // for each file divided into chunks
        // store those chunks in vector Db
        List<Document> Chunks = new ArrayList<>();

        TokenTextSplitter splitter = TokenTextSplitter
                .builder()
                .withChunkSize(300)
                .build();

        for(Resource resource:resourcesFiles){
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
            List<Document> page = reader.read();
            List<Document> chunks = splitter.apply(page);
            Chunks.addAll(chunks);
        }
        vectorStore.add(Chunks);
    }

    public String query(String question){
        // Question --> vector
        // similarity search
        // fetch top result
        List<Document> Chunk = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .build()
        );
        StringBuilder context = new StringBuilder();



        for(Document document:Chunk){
            context.append(document.getText()).append("\n\n");
        }
        String prompt = getPrompt(question, context);
        return chatClient.prompt()
                .system(prompt)
                .user(question)
                .call()
                .content();
    }

    private static @NonNull String getPrompt(String question, StringBuilder context) {
        String finalContext = context.toString();

        return """
                    Your are an AI customer support assistant for e-commerce platform
                    Answer the customer using only the company information provided below.
                    If the answer is not available in the provided information , say:
                    "I don't have that information in the company."
                    
                    Company Information
                    %s
                    User query 
                    %s
                """.formatted(finalContext , question);
    }
}
