package com.akashic.kunftig.diagnostics;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Command line of {@link ObfuscatedApiScanner}: prints the findings and exits with 0 (clean), 1 (findings) or 2
 * (usage). Kept in its own class because it calls {@code System.exit}: FML's {@code TerminalTransformer} logs a
 * "THIS IS NOT ALLOWED" warning for every loaded class that does, and the mod never loads this one (issue #6).
 */
public final class ObfuscatedApiScannerCli {

    private ObfuscatedApiScannerCli() {}

    /** {@code java -cp kunftig.jar com.akashic.kunftig.diagnostics.ObfuscatedApiScannerCli <server.jar> [prefix...]} */
    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    /** Runs the scan and returns the exit code. */
    static int run(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: ObfuscatedApiScanner <server.jar> [package/prefix/ ...]");
            return 2;
        }
        List<String> includes = args.length > 1 ? Arrays.asList(args)
            .subList(1, args.length) : ObfuscatedApiScanner.DEFAULT_INCLUDES;
        List<ObfuscatedApiScanner.Finding> findings = ObfuscatedApiScanner
            .scan(new File(args[0]), includes, ObfuscatedApiScanner.DEFAULT_EXCLUDES);
        for (ObfuscatedApiScanner.Finding finding : findings) {
            System.out.println(finding);
        }
        System.out.println(findings.size() + " member(s) name default-package classes");
        return findings.isEmpty() ? 0 : 1;
    }
}
