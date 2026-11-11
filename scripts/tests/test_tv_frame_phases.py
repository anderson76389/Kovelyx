import unittest
from tv_frame_phases import analyse

HEADER = 'Flags,IntendedVsync,HandleInputStart,SyncQueued,IssueDrawCommandsStart,SwapBuffers,FrameCompleted,GpuCompleted,'
FRAME = '0,1000000000,1001000000,1004000000,1006000000,1008000000,1030000000,9223372036854775807,'


class FramePhaseTests(unittest.TestCase):
    def test_gpu_sentinel_does_not_turn_ui_work_into_seconds_of_gpu_work(self):
        r = analyse(HEADER + '\n' + FRAME)
        self.assertEqual(3, r['phases']['ui']['p50_ms'])
        self.assertEqual(22, r['phases']['after_swap']['p50_ms'])
        self.assertEqual(30, r['phases']['total']['p50_ms'])

    def test_duplicate_window_dump_is_not_counted_twice(self):
        r = analyse('\n'.join([HEADER, FRAME, HEADER, FRAME]))
        self.assertEqual(1, r['valid_frames'])

    def test_incomplete_and_nonmonotonic_frame_is_rejected(self):
        bad = FRAME.replace('1030000000', '9223372036854775807')
        r = analyse('\n'.join([HEADER, FRAME, bad, '0,1000000000']))
        self.assertEqual(1, r['valid_frames'])
        self.assertEqual(2, r['rejected_frames'])

    def test_missing_data_fails_instead_of_claiming_zero_jank(self):
        with self.assertRaises(ValueError):
            analyse('No frames')


if __name__ == '__main__':
    unittest.main()
