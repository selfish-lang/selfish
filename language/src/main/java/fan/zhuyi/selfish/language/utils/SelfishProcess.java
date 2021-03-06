package fan.zhuyi.selfish.language.utils;

import java.io.File;
import java.io.FileDescriptor;

public class SelfishProcess {
    /*
     * NO bi-direction pipe for now.
     */
    public static abstract class IOPipe {
        public static final int PIPE_INPUT = 0;
        public static final int PIPE_OUTPUT = 1;
        public static final int PIPE_APPEND = 23;
        protected final int direction;
        protected final FileDescriptor sourceDescriptor;

        protected IOPipe(int direction, FileDescriptor sourceDescriptor) {
            this.direction = direction;
            this.sourceDescriptor = sourceDescriptor;
        }

        public abstract ProcessBuilder.Redirect getRedirect();
    }

    public static class FilePipe extends IOPipe {
        private final File file;

        public FilePipe(File file, int direction, FileDescriptor sourceDescriptor) {
            super(direction, sourceDescriptor);
            this.file = file;
        }

        @Override
        public ProcessBuilder.Redirect getRedirect() {
            switch (direction) {
                case PIPE_INPUT:
                    return ProcessBuilder.Redirect.from(file);
                case PIPE_APPEND:
                    return ProcessBuilder.Redirect.appendTo(file);
                case PIPE_OUTPUT:
                    return ProcessBuilder.Redirect.to(file);
                default:
                    throw new IllegalArgumentException("invalid pipeline direction");
            }
        }
    }

    public static class StdPipe extends IOPipe {
        public StdPipe(int direction, FileDescriptor sourceDescriptor) {
            super(direction, sourceDescriptor);
        }

        @Override
        public ProcessBuilder.Redirect getRedirect() {
            return ProcessBuilder.Redirect.PIPE;
        }


    }

    public static class ClosedPipe extends IOPipe {
        protected ClosedPipe(int direction, FileDescriptor sourceDescriptor) {
            super(direction, sourceDescriptor);
        }

        @Override
        public ProcessBuilder.Redirect getRedirect() {
            return ProcessBuilder.Redirect.DISCARD;
        }
    }
}
