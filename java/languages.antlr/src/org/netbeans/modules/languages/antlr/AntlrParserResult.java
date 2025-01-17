/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.languages.antlr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.netbeans.modules.csl.api.Error;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.csl.spi.DefaultError;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lkishalmi
 */
public abstract class AntlrParserResult<T extends Parser> extends ParserResult {

    public final List<DefaultError> errors = new ArrayList<>();
    public final Map<String, Reference> references = new TreeMap<>();

    public final List<OffsetRange> folds = new ArrayList<>();
    public final List<AntlrStructureItem> structure = new ArrayList<>();

    volatile boolean finished = false;

    public AntlrParserResult(Snapshot snapshot) {
        super(snapshot);
    }

    public AntlrParserResult get() {
        if (! finished) {
            FileObject fo = getSnapshot().getSource().getFileObject();
            T parser = createParser(getSnapshot());
            parser.addErrorListener(createErrorListener());
            parser.addParseListener(createFoldListener());
            parser.addParseListener(createReferenceListener());
            parser.addParseListener(createImportListener());
            parser.addParseListener(createStructureListener());
            parser.addParseListener(createOccurancesListener());
            evaluateParser(parser);

            // Start a second parsing phase for checking;
            parser = createParser(getSnapshot());
            parser.addParseListener(createCheckReferences());
            evaluateParser(parser);
            finished = true;
        }
        return this;
    }

    @Override
    public List<? extends Error> getDiagnostics() {
        return errors;
    }

    @Override
    protected void invalidate() {
        //references.clear();
    }

    @Override
    protected boolean processingFinished() {
        return finished;
    }

    public static class Reference {
        public final String name;
        public FileObject source;
        public OffsetRange defOffset;
        public final List<OffsetRange> occurances = new ArrayList<>();

        public Reference(String name, FileObject source, OffsetRange defOffset) {
            this.name = name;
            this.source = source;
            this.defOffset = defOffset;
        }
    }

    protected final FileObject getFileObject() {
        return getSnapshot().getSource().getFileObject();
    }

    protected abstract T createParser(Snapshot snapshot);
    protected abstract void evaluateParser(T parser);

    protected abstract ParseTreeListener createReferenceListener();
    protected abstract ParseTreeListener createImportListener();
    protected abstract ParseTreeListener createFoldListener();
    protected abstract ParseTreeListener createStructureListener();
    protected abstract ParseTreeListener createOccurancesListener();

    protected abstract ParseTreeListener createCheckReferences();

    protected ANTLRErrorListener createErrorListener() {
        return new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                int errorPosition = 0;
                if (offendingSymbol instanceof Token) {
                    Token offendingToken = (Token) offendingSymbol;
                    errorPosition = offendingToken.getStartIndex();
                }
                errors.add(new DefaultError(null, msg, null, getFileObject(), errorPosition, errorPosition, Severity.ERROR));
            }

        };
    }


}
