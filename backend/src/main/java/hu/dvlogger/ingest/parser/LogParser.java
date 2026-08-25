package hu.dvlogger.ingest.parser;

import hu.dvlogger.model.LogEntry;

/** Implementations must never throw; on failure return LogEntry.unparsed(raw, remoteIp). */
public interface LogParser {
  LogEntry parse(String raw, String remoteIp);
}
