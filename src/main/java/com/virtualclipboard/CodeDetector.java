package com.virtualclipboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust programming language detector using a multi-layered scoring system.
 * Analyzes structural markers, token density, language-specific keywords, 
 * and natural language patterns to distinguish code from normal text.
 */
public class CodeDetector {

    private static final int MIN_CODE_SCORE = 6; // Higher threshold for better accuracy
    
    // Common English words that often appear in sentences but rarely in code (except comments)
    private static final Set<String> COMMON_TEXT_WORDS = new HashSet<>();
    static {
        String[] words = {
            "the", "and", "that", "have", "for", "not", "with", "you", "this", "but", 
            "his", "from", "they", "say", "her", "she", "will", "one", "all", "would", 
            "there", "their", "what", "about", "who", "get", "which", "go", "me", "when", 
            "make", "can", "like", "time", "just", "him", "know", "take", "people", "into", 
            "year", "your", "good", "some", "could", "them", "see", "other", "than", "then", 
            "now", "look", "only", "come", "its", "over", "think", "also", "back", "after", 
            "use", "two", "how", "our", "work", "first", "well", "way", "even", "new", 
            "want", "because", "any", "these", "give", "day", "most", "us", "is", "are", 
            "was", "were", "been", "has", "had", "does", "did", "be", "being", "should"
        };
        for (String w : words) COMMON_TEXT_WORDS.add(w);
    }

    // High-confidence code tokens (unique to programming)
    private static final Set<String> UNIVERSAL_CODE_TOKENS = new HashSet<>();
    static {
        String[] tokens = {
            "public", "private", "protected", "static", "final", "void", "int", "float", 
            "double", "boolean", "char", "String", "if", "else", "for", "while", "do", 
            "switch", "case", "default", "break", "continue", "return", "class", "interface", 
            "extends", "implements", "package", "import", "throws", "throw", "try", "catch", 
            "finally", "synchronized", "volatile", "transient", "native", "strictfp", "enum", 
            "assert", "new", "this", "super", "instanceof", "null", "true", "false", 
            "def", "elif", "lambda", "yield", "with", "async", "await", "self", "None", 
            "function", "const", "let", "var", "export", "require", "module", "undefined", 
            "namespace", "using", "foreach", "override", "virtual", "nullptr", "sizeof", 
            "struct", "typedef", "union", "unsigned", "signed", "extern", "inline", 
            "template", "typename", "pub", "fn", "mut", "impl", "trait", "where", 
            "type", "func", "chan", "defer", "go", "select", "val", "fun", "lateinit", 
            "suspend", "companion", "data", "guard", "extension", "protocol", "init"
        };
        for (String t : tokens) UNIVERSAL_CODE_TOKENS.add(t);
    }

    /**
     * Detects the programming language of a given text snippet.
     * @param text The text to analyze.
     * @return The name of the detected language, or null if not recognized as code.
     */
    public static String detectLanguage(String text) {
        if (text == null || text.length() < 15) return null;
        
        String t = text.trim();
        String lower = t.toLowerCase();
        String upper = t.toUpperCase();
        
        // --- 1. Natural Language vs Code Density Analysis ---
        double codeDensity = calculateCodeDensity(t);
        int textPenalty = calculateNaturalLanguagePenalty(t);
        
        // --- 2. Structural Analysis ---
        int structuralScore = calculateStructuralScore(t, upper);
        
        // Early exit for obvious normal text
        if (codeDensity < 0.1 && structuralScore < 2 && textPenalty > 5) {
            return null;
        }

        Map<String, Integer> scores = new HashMap<>();

        // 3. Language-Specific Scoring
        scores.put("Java", scoreJava(t));
        scores.put("Python", scorePython(t));
        scores.put("JavaScript", scoreJavaScript(t));
        scores.put("C++", scoreCpp(t));
        scores.put("C", scoreC(t));
        scores.put("C#", scoreCsharp(t));
        scores.put("HTML", scoreHtml(t));
        scores.put("CSS", scoreCss(t, lower));
        scores.put("SQL", scoreSql(upper));
        scores.put("PHP", scorePhp(t));
        scores.put("Ruby", scoreRuby(t));
        scores.put("Markdown", scoreMarkdown(t));
        scores.put("Go", scoreGo(t));
        scores.put("Rust", scoreRust(t));
        scores.put("Swift", scoreSwift(t));
        scores.put("Kotlin", scoreKotlin(t));
        scores.put("Shell", scoreShell(t));
        scores.put("YAML", scoreYaml(t));

        // Find the language with the highest score
        String bestLanguage = null;
        int maxScore = 0;

        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                bestLanguage = entry.getKey();
            }
        }

        // Final Validation: Combine scores and penalties
        int finalScore = maxScore + structuralScore - textPenalty;
        
        // Minimum threshold check
        if (finalScore < MIN_CODE_SCORE) return null;
        if (maxScore < 4 && structuralScore < 3) return null; 

        return bestLanguage;
    }

    private static double calculateCodeDensity(String t) {
        String[] tokens = t.split("[\\s\\W]+");
        if (tokens.length == 0) return 0.0;
        
        int codeTokenCount = 0;
        for (String token : tokens) {
            if (UNIVERSAL_CODE_TOKENS.contains(token)) {
                codeTokenCount++;
            }
        }
        return (double) codeTokenCount / tokens.length;
    }

    private static int calculateNaturalLanguagePenalty(String t) {
        int penalty = 0;
        
        // Sequence of non-code tokens check
        String[] words = t.split("\\s+");
        int consecutiveNonTokens = 0;
        int maxConsecutive = 0;
        
        for (String word : words) {
            String cleanWord = word.replaceAll("[^a-zA-Z]", "");
            if (cleanWord.isEmpty()) continue;
            
            if (!UNIVERSAL_CODE_TOKENS.contains(cleanWord)) {
                consecutiveNonTokens++;
                if (COMMON_TEXT_WORDS.contains(cleanWord.toLowerCase())) {
                    penalty += 1; // Direct penalty for common text words
                }
            } else {
                maxConsecutive = Math.max(maxConsecutive, consecutiveNonTokens);
                consecutiveNonTokens = 0;
            }
        }
        maxConsecutive = Math.max(maxConsecutive, consecutiveNonTokens);
        
        // Large sequences of non-tokens are a strong indicator of natural language
        if (maxConsecutive > 5) penalty += (maxConsecutive - 5) * 2;
        
        // Sentence markers (Capitalized word followed by lowercase words and a period)
        Pattern sentencePattern = Pattern.compile("[A-Z][a-z]+\\s+[a-z]+\\s+[a-z]+[\\.\\?!]");
        Matcher m = sentencePattern.matcher(t);
        while (m.find()) penalty += 3;

        return penalty;
    }

    private static int calculateStructuralScore(String t, String upper) {
        int score = 0;
        if (countOccurrences(t, "{") > 0 && countOccurrences(t, "}") > 0) score += 2;
        if (countOccurrences(t, ";") > 1) score += 2;
        if (countOccurrences(t, "(") > 0 && countOccurrences(t, ")") > 0) score += 1;
        if (t.contains("    ") || t.contains("\t")) score += 1;
        if (t.contains("//") || t.contains("/*") || (t.contains("#") && !t.contains(" #"))) score += 1;
        
        // Special structural markers
        if (t.contains(" => ") || t.contains(" -> ")) score += 2;
        if (t.contains(" == ") || t.contains(" != ")) score += 1;
        if (t.contains(" && ") || t.contains(" || ")) score += 1;
        
        return score;
    }

    // --- Language Specific Scoring Functions ---

    private static int scoreJava(String t) {
        int s = 0;
        if (t.contains("public class ")) s += 5;
        if (t.contains("public static void main")) s += 6;
        if (t.contains("System.out.print")) s += 4;
        if (t.contains("@Override") || t.contains("@Test")) s += 4;
        if (t.contains("package ") && t.contains(";")) s += 3;
        if (t.contains("import java.") || t.contains("import static ")) s += 3;
        if (t.contains("ArrayList<") || t.contains("HashMap<")) s += 3;
        if (t.contains("private final ") || t.contains("protected void ")) s += 3;
        if (t.contains("throws Exception") || t.contains("try {") && t.contains("catch (")) s += 3;
        if (t.contains("implements Serializable") || t.contains("extends ")) s += 2;
        if (t.contains("StringBuilder") || t.contains("StringBuffer")) s += 2;
        if (t.contains("Stream.") && t.contains(".collect")) s += 4;
        if (t.contains("Collectors.") || t.contains("Optional.")) s += 3;
        if (t.contains("Iterator ") || t.contains("Iterable ")) s += 2;
        if (t.contains("Thread.sleep") || t.contains("Runnable ")) s += 3;
        return s;
    }

    private static int scorePython(String t) {
        int s = 0;
        if (t.contains("def ") && t.contains("):")) s += 5;
        if (t.contains("if __name__ == \"__main__\":")) s += 7;
        if (t.contains("import ") && (t.contains("as ") || t.contains("from "))) s += 4;
        if (t.contains("self.") && t.contains("def ")) s += 5;
        if (t.contains("elif ") || t.contains("lambda ")) s += 4;
        if (t.contains("async def ") || t.contains("await ")) s += 4;
        if (t.contains("range(") || t.contains("enumerate(")) s += 2;
        if (t.contains("__init__") || t.contains("__str__")) s += 5;
        if (t.contains("try:") && t.contains("except ")) s += 4;
        if (t.contains(":") && (t.contains("    ") || t.contains("\t"))) s += 2;
        if (t.contains("with open(") || t.contains("pip install ")) s += 4;
        if (t.contains("@staticmethod") || t.contains("@classmethod")) s += 4;
        if (t.contains("super().__init__")) s += 5;
        if (t.contains("import os") || t.contains("import sys")) s += 2;
        if (t.contains("print(f\"")) s += 3;
        return s;
    }

    private static int scoreJavaScript(String t) {
        int s = 0;
        if (t.contains("function ") && t.contains("{")) s += 3;
        if (t.contains("console.log(")) s += 4;
        if (t.contains("=>") && (t.contains("const") || t.contains("("))) s += 4;
        if (t.contains("export default") || t.contains("import {") || t.contains("import React")) s += 5;
        if (t.contains("useEffect(") || t.contains("useState(") || t.contains("useContext(")) s += 6;
        if (t.contains("document.getElementById") || t.contains("window.addEventListener")) s += 5;
        if (t.contains("JSON.stringify(") || t.contains("JSON.parse(")) s += 3;
        if (t.contains("require('") && t.contains("module.exports")) s += 5;
        if (t.contains("async ") && t.contains("await ")) s += 3;
        if (t.contains("window.") || t.contains("localStorage.")) s += 2;
        if (t.contains("fetch(") || t.contains("axios.")) s += 4;
        if (t.contains("Promise.all(") || t.contains(".then(")) s += 4;
        if (t.contains(".catch(") || t.contains(".finally(")) s += 2;
        if (t.contains("typeof ") || t.contains("instanceof ")) s += 2;
        if (t.contains("undefined") || t.contains("null")) s += 1;
        return s;
    }

    private static int scoreCpp(String t) {
        int s = 0;
        if (t.contains("#include <") && t.contains(">")) s += 6;
        if (t.contains("std::") && (t.contains("cout") || t.contains("endl") || t.contains("vector"))) s += 5;
        if (t.contains("using namespace std;")) s += 7;
        if (t.contains("public:") || t.contains("private:") || t.contains("protected:")) s += 4;
        if (t.contains("template<typename") || t.contains("template <class")) s += 5;
        if (t.contains("virtual ") && t.contains(" = 0;")) s += 5;
        if (t.contains("dynamic_cast<") || t.contains("static_cast<")) s += 5;
        if (t.contains("nullptr")) s += 3;
        if (t.contains("std::string") || t.contains("std::vector")) s += 3;
        if (t.contains("std::map") || t.contains("std::unordered_map")) s += 3;
        if (t.contains("std::unique_ptr") || t.contains("std::shared_ptr")) s += 5;
        if (t.contains("#pragma once")) s += 6;
        if (t.contains("operator") && (t.contains("==") || t.contains("<<"))) s += 4;
        if (t.contains("reinterpret_cast<") || t.contains("const_cast<")) s += 5;
        if (t.contains("inline ") || t.contains("extern \"C\"")) s += 3;
        return s;
    }

    private static int scoreC(String t) {
        int s = 0;
        if (t.contains("#include <stdio.h>") || t.contains("#include <stdlib.h>")) s += 6;
        if (t.contains("malloc(") || t.contains("free(") || t.contains("sizeof(")) s += 4;
        if (t.contains("printf(\"") && !t.contains("System.")) s += 3;
        if (t.contains("struct ") && t.contains("{")) s += 2;
        if (t.contains("typedef struct")) s += 5;
        if (t.contains("int main(int argc")) s += 7;
        if (t.contains("#define ") && t.contains(" ") && !t.contains("\n")) s += 3;
        if (t.contains("#include <string.h>") || t.contains("#include <errno.h>")) s += 5;
        if (t.contains("fprintf(stderr")) s += 4;
        if (t.contains("void*") || t.contains("char*")) s += 3;
        if (t.contains("unsigned char") || t.contains("long long")) s += 3;
        if (t.contains("volatile ") || t.contains("restrict ")) s += 3;
        if (t.contains("goto ") && t.contains(":")) s += 4;
        if (t.contains("uint32_t") || t.contains("int64_t")) s += 4;
        if (t.contains("#ifndef ") && t.contains("#define ") && t.contains("#endif")) s += 5;
        return s;
    }

    private static int scoreCsharp(String t) {
        int s = 0;
        if (t.contains("using System;") || t.contains("using Microsoft.")) s += 5;
        if (t.contains("Console.WriteLine(")) s += 5;
        if (t.contains("namespace ") && t.contains("{")) s += 3;
        if (t.contains("public class ") && t.contains("{ get; set; }")) s += 7;
        if (t.contains("foreach(var ") || t.contains("Task.Run(")) s += 4;
        if (t.contains("await ") && t.contains("async Task")) s += 4;
        if (t.contains("[HttpGet]") || t.contains("[HttpPost]")) s += 5;
        if (t.contains("List<") || t.contains("Dictionary<")) s += 3;
        if (t.contains("IEnumerable<") || t.contains("IQueryable<")) s += 4;
        if (t.contains("lock(") || t.contains("using (")) s += 3;
        if (t.contains("var ") && t.contains(" = new ")) s += 2;
        if (t.contains("delegate ") || t.contains("event ")) s += 4;
        if (t.contains("sealed class") || t.contains("partial class")) s += 4;
        if (t.contains("Console.ReadLine()")) s += 4;
        if (t.contains("yield return ")) s += 5;
        return s;
    }

    private static int scoreHtml(String t) {
        int s = 0;
        if (t.contains("<!DOCTYPE html>") || t.contains("<html")) s += 6;
        if (t.contains("</div>") || t.contains("</span>") || t.contains("</p>")) s += 3;
        if (t.contains("<body") || t.contains("<head") || t.contains("<title>")) s += 4;
        if (t.contains("<script ") || t.contains("<link rel=") || t.contains("<meta ")) s += 4;
        if (t.contains("href=") || t.contains("src=") || t.contains("class=") || t.contains("id=")) s += 1;
        if (t.contains("<input ") || t.contains("<form ") || t.contains("<button ")) s += 4;
        if (t.contains("<ul>") && t.contains("<li>")) s += 3;
        if (t.contains("<table>") || t.contains("<tr>")) s += 3;
        if (t.contains("<td>") || t.contains("<th>")) s += 2;
        if (t.contains("<img>") || t.contains("<a>")) s += 2;
        if (t.contains("<p>") || t.contains("<h1>")) s += 1;
        if (t.contains("<thead>") || t.contains("<tbody>")) s += 3;
        if (t.contains("<footer>") || t.contains("<header>")) s += 3;
        if (t.contains("<nav>") || t.contains("<section>")) s += 3;
        if (t.contains("alt=") || t.contains("title=")) s += 1;
        return s;
    }

    private static int scoreCss(String t, String lower) {
        int s = 0;
        if (t.matches("(?s).*[a-zA-Z0-9_-]+\\s*\\{\\s*[a-zA-Z-]+\\s*:\\s*[^;]+;.*\\}")) s += 7;
        if (lower.contains("background-color:") || lower.contains("display: flex;") || lower.contains("font-family:")) s += 5;
        if (lower.contains("margin:") || lower.contains("padding:") || lower.contains("border:")) s += 1;
        if (lower.contains("@media ") || lower.contains("@keyframes ") || lower.contains("@import ")) s += 5;
        if (lower.contains("!important")) s += 4;
        if (lower.contains("justify-content:") || lower.contains("align-items:")) s += 4;
        if (lower.contains("color:") || lower.contains("font-size:")) s += 2;
        if (lower.contains("text-align:") || lower.contains("text-decoration:")) s += 2;
        if (lower.contains("position: absolute;") || lower.contains("position: relative;")) s += 4;
        if (lower.contains("z-index:") || lower.contains("opacity:")) s += 3;
        if (lower.contains("transition:") || lower.contains("transform:")) s += 4;
        if (lower.contains("box-sizing:") || lower.contains("cursor:")) s += 3;
        if (lower.contains("flex-direction:") || lower.contains("grid-template-")) s += 5;
        if (lower.contains("width:") || lower.contains("height:")) s += 1;
        if (lower.contains("border-radius:") || lower.contains("box-shadow:")) s += 3;
        return s;
    }

    private static int scoreSql(String upper) {
        int s = 0;
        if (upper.contains("SELECT ") && upper.contains("FROM ")) s += 5;
        if (upper.contains("INSERT INTO ") || upper.contains("UPDATE ") || upper.contains("DELETE FROM ")) s += 5;
        if (upper.contains("CREATE TABLE ") || upper.contains("DROP TABLE ") || upper.contains("ALTER TABLE ")) s += 5;
        if (upper.contains("WHERE ") && (upper.contains(" = ") || upper.contains(" IN "))) s += 3;
        if (upper.contains("JOIN ") && upper.contains(" ON ")) s += 5;
        if (upper.contains("GROUP BY ") || upper.contains("ORDER BY ")) s += 4;
        if (upper.contains("PRIMARY KEY") || upper.contains("FOREIGN KEY")) s += 5;
        if (upper.contains("DISTINCT ") || upper.contains("COUNT(")) s += 3;
        if (upper.contains("HAVING ") || upper.contains("UNION ALL")) s += 4;
        if (upper.contains("BETWEEN ") && upper.contains(" AND ")) s += 3;
        if (upper.contains("LIKE '%") || upper.contains("IS NULL")) s += 3;
        if (upper.contains("DESC") || upper.contains("ASC")) s += 2;
        if (upper.contains("LIMIT ") || upper.contains("OFFSET ")) s += 3;
        if (upper.contains("INTO ") && upper.contains("VALUES")) s += 4;
        if (upper.contains("TRUNCATE TABLE") || upper.contains("RENAME TO")) s += 5;
        return s;
    }

    private static int scorePhp(String t) {
        int s = 0;
        if (t.contains("<?php")) s += 8;
        if (t.contains("$this->") || t.contains("$_GET[") || t.contains("$_POST[")) s += 5;
        if (t.contains("echo \"") && t.contains(";")) s += 3;
        if (t.contains("public function ") && t.contains("{")) s += 4;
        if (t.contains("mysqli_connect") || t.contains("PDO::")) s += 6;
        if (t.contains("foreach($") && t.contains("as $")) s += 4;
        if (t.contains("var_dump(") || t.contains("print_r(")) s += 4;
        if (t.contains("require_once") || t.contains("include_once")) s += 4;
        if (t.contains("die(") || t.contains("exit(")) s += 3;
        if (t.contains("namespace ") || t.contains("use ")) s += 3;
        if (t.contains("parent::") || t.contains("self::")) s += 4;
        if (t.contains("public static function") || t.contains("private function")) s += 4;
        if (t.contains("array(") || t.contains("[]")) s += 1;
        if (t.contains("isset(") || t.contains("empty(")) s += 3;
        if (t.contains("json_encode(") || t.contains("json_decode(")) s += 3;
        return s;
    }

    private static int scoreRuby(String t) {
        int s = 0;
        if (t.contains("def ") && t.contains("end") && (t.contains("puts ") || t.contains("require "))) s += 6;
        if (t.contains("attr_accessor ") || t.contains("attr_reader") || t.contains("attr_writer")) s += 5;
        if (t.contains("class ") && t.contains(" < ")) s += 4;
        if (t.contains(".each do |") || t.contains(".map { |")) s += 5;
        if (t.contains("ActiveRecord::Base") || t.contains("ActionController::Base")) s += 6;
        if (t.contains("rescue ") && t.contains("ensure")) s += 4;
        if (t.contains("require '") || t.contains("module ")) s += 3;
        if (t.contains("include ") || t.contains("extend ")) s += 3;
        if (t.contains("yield") && t.contains("def ")) s += 4;
        if (t.contains("alias_method") || t.contains("private")) s += 4;
        if (t.contains("initialize") || t.contains("self.")) s += 3;
        if (t.contains("nil?") || t.contains("empty?")) s += 3;
        if (t.contains("|f|") || t.contains("|line|")) s += 2;
        if (t.contains("#{") && t.contains("}")) s += 3;
        if (t.contains("gem '") || t.contains("bundle exec")) s += 5;
        return s;
    }

    private static int scoreMarkdown(String t) {
        int s = 0;
        if (t.startsWith("# ") || t.startsWith("## ") || t.startsWith("### ")) s += 4;
        if (t.contains("```")) s += 7;
        if (t.contains("[") && t.contains("](") && t.contains(")")) s += 4;
        if (t.contains("**") && t.contains("**")) s += 2;
        if (t.contains("- [ ]") || t.contains("- [x]")) s += 5;
        if (t.contains("|") && t.contains("---")) s += 4;
        if (t.contains("* ") || t.contains("> ")) s += 2;
        if (t.contains("1. ") || t.contains("2. ")) s += 2;
        if (t.contains("---") || t.contains("***")) s += 2;
        if (t.contains("~~") && t.contains("~~")) s += 3;
        if (t.contains("![](") || t.contains("<u>")) s += 3;
        if (t.contains("| :--- |") || t.contains("| :---: |")) s += 5;
        if (t.contains("<u>") || t.contains("<br>")) s += 2;
        if (t.contains("    ") && t.contains("\n    ")) s += 2;
        if (t.contains("[^1]:")) s += 4;
        return s;
    }

    private static int scoreGo(String t) {
        int s = 0;
        if (t.contains("package main")) s += 6;
        if (t.contains("import \"fmt\"") || t.contains("fmt.Println(")) s += 5;
        if (t.contains("func ") && t.contains("(") && t.contains(")") && t.contains("{")) s += 4;
        if (t.contains(":= ")) s += 4;
        if (t.contains("go func(")) s += 5;
        if (t.contains("chan ") || t.contains("defer ") || t.contains("select {")) s += 4;
        if (t.contains("interface {") || t.contains("struct {")) s += 3;
        if (t.contains("import (") || t.contains("type ")) s += 3;
        if (t.contains("map[") || t.contains("make(")) s += 3;
        if (t.contains("append(") || t.contains("panic(")) s += 3;
        if (t.contains("recover()") || t.contains("len(")) s += 2;
        if (t.contains("context.Context") || t.contains("http.Handler")) s += 5;
        if (t.contains("err != nil") || t.contains("if err := ")) s += 5;
        if (t.contains("go.mod") || t.contains("go.sum")) s += 6;
        if (t.contains("func (") && t.contains(") ")) s += 4;
        return s;
    }

    private static int scoreRust(String t) {
        int s = 0;
        if (t.contains("fn main()") || t.contains("pub fn ")) s += 5;
        if (t.contains("let mut ") || t.contains("let ")) s += 4;
        if (t.contains("println!(")) s += 5;
        if (t.contains("match ") && t.contains("=>")) s += 5;
        if (t.contains("impl ") && t.contains("for ")) s += 5;
        if (t.contains("unwrap()") || t.contains("expect(")) s += 3;
        if (t.contains("Box::new(") || t.contains("Vec::new()")) s += 4;
        if (t.contains("use ") || t.contains("mod ")) s += 3;
        if (t.contains("mut ") || t.contains("static ")) s += 2;
        if (t.contains("const ") || t.contains("extern ")) s += 3;
        if (t.contains("trait ") || t.contains("struct ")) s += 3;
        if (t.contains("Cargo.toml") || t.contains("Option<")) s += 5;
        if (t.contains("Result<") || t.contains("Ok(")) s += 4;
        if (t.contains("Err(") || t.contains("Vec<")) s += 3;
        if (t.contains("macro_rules!")) s += 6;
        return s;
    }

    private static int scoreSwift(String t) {
        int s = 0;
        if (t.contains("import UIKit") || t.contains("import Foundation")) s += 6;
        if (t.contains("func ") && t.contains("->")) s += 5;
        if (t.contains("guard let ") || t.contains("if let ")) s += 5;
        if (t.contains("@IBOutlet") || t.contains("@IBAction") || t.contains("@objc")) s += 6;
        if (t.contains("override func ") || t.contains("extension ")) s += 4;
        if (t.contains("print(\"\\(") && t.contains("\")")) s += 4;
        if (t.contains("let ") || t.contains("var ")) s += 1;
        if (t.contains("class ") || t.contains("struct ")) s += 2;
        if (t.contains("enum ") || t.contains("protocol ")) s += 3;
        if (t.contains("extension ") || t.contains("static ")) s += 3;
        if (t.contains("public ") || t.contains("private ")) s += 1;
        if (t.contains("fileprivate") || t.contains("internal")) s += 4;
        if (t.contains("case ") && t.contains("let ")) s += 4;
        if (t.contains("throws") && t.contains("try ")) s += 4;
        if (t.contains("async") && t.contains("await")) s += 3;
        return s;
    }

    private static int scoreKotlin(String t) {
        int s = 0;
        if (t.contains("package ") && t.contains("val ")) s += 4;
        if (t.contains("fun ") && t.contains("{")) s += 4;
        if (t.contains("data class ") || t.contains("sealed class ")) s += 5;
        if (t.contains("companion object") || t.contains("lateinit var")) s += 5;
        if (t.contains("suspend fun") || t.contains("coroutineScope")) s += 5;
        if (t.contains("?.let {") || t.contains("!!.")) s += 4;
        if (t.contains("val ") || t.contains("var ")) s += 1;
        if (t.contains("class ") || t.contains("object ")) s += 2;
        if (t.contains("interface ") || t.contains("override ")) s += 2;
        if (t.contains("abstract ") || t.contains("open ")) s += 3;
        if (t.contains("internal ") || t.contains("private ")) s += 2;
        if (t.contains("listOf(") || t.contains("mapOf(")) s += 4;
        if (t.contains("when (") && t.contains("->")) s += 5;
        if (t.contains("fun main(") || t.contains("println(")) s += 4;
        if (t.contains("@JvmStatic") || t.contains("@Inject")) s += 5;
        return s;
    }

    private static int scoreShell(String t) {
        int s = 0;
        if (t.startsWith("#!/bin/")) s += 8;
        if (t.contains("echo ") && (t.contains("export ") || t.contains("sudo "))) s += 5;
        if (t.contains("if [") && t.contains("]; then")) s += 6;
        if (t.contains("apt-get install") || t.contains("yum install") || t.contains("brew install")) s += 6;
        if (t.contains(">> /dev/null") || t.contains("2>&1")) s += 5;
        if (t.contains("grep ") || t.contains("awk ") || t.contains("sed ")) s += 3;
        if (t.contains("#!/bin/bash") || t.contains("#!/bin/sh")) s += 8;
        if (t.contains("set -e") || t.contains("set -x")) s += 5;
        if (t.contains("alias ") || t.contains("source ")) s += 3;
        if (t.contains("exec ") || t.contains("exit ")) s += 2;
        if (t.contains("wait ") || t.contains("sleep ")) s += 2;
        if (t.contains("chmod +x") || t.contains("chown ")) s += 5;
        if (t.contains("while read ") || t.contains("for i in ")) s += 4;
        if (t.contains("$(command -v") || t.contains("`which ")) s += 5;
        if (t.contains("dirname \"$0\"") || t.contains("basename ")) s += 5;
        return s;
    }

    private static int scoreYaml(String t) {
        int s = 0;
        if (t.contains("version: ") && t.contains("services:")) s += 6;
        if (t.contains("image: ") && t.contains("ports:")) s += 5;
        if (t.contains(": ") && t.contains("\n  ")) s += 4;
        if (t.contains("    - ") || t.contains("  - ")) s += 3;
        if (t.contains("environment:") || t.contains("volumes:")) s += 4;
        if (t.contains("---") || t.contains("...")) s += 3;
        if (t.contains("!!") || t.contains("&") || t.contains("*")) s += 3;
        if (t.contains("? ") || t.contains("> ") || t.contains("| ")) s += 3;
        if (t.contains("true") || t.contains("false") || t.contains("null")) s += 1;
        if (t.contains("api_version:") || t.contains("kind:")) s += 6;
        if (t.contains("metadata:") || t.contains("spec:")) s += 5;
        if (t.contains("tags:") || t.contains("labels:")) s += 3;
        if (t.contains("enabled:") || t.contains("disabled:")) s += 3;
        if (t.contains("  #") && t.contains("\n  ")) s += 2;
        if (t.contains("defaults:") || t.contains("profiles:")) s += 4;
        return s;
    }

    private static int countOccurrences(String text, String target) {
        if (text == null || target == null || target.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }
}
