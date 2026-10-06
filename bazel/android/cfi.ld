SECTIONS {
  .eh_frame (INFO) : { KEEP(*(.eh_frame)) }
}
INSERT AFTER .text;
